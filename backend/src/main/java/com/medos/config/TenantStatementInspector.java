package com.medos.config;

import com.medos.security.TenantContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rewrites SQL at prepare time to scope tenant-owned tables to the current tenant.
 *
 * <p>Injects {@code <alias>.tenant_id = ?} into the root FROM clause of SELECT
 * statements when {@link TenantContext} holds a tenant. The binder happens before
 * execution, so it is robust regardless of Hibernate session/filter wiring.
 *
 * <p>G16 (Wave-1 substrate): the same root-table rule now covers <b>writes</b>.
 * UPDATE and DELETE on a tenant table get the tenant predicate folded into their
 * top-level WHERE (or become it, for a bulk statement that has none), and an INSERT
 * into a tenant table whose column list omits {@code tenant_id} fails loudly — an
 * orphan row is unreachable by every tenant-scoped read, so writing one silently is
 * worse than refusing. Hibernate names every mapped column on its own INSERTs, so
 * the loud guard only fires for foreign SQL: native repository queries, external
 * tools, mistakes. The listener stamps new entities; this refuses anything that
 * slips past it.
 *
 * <p>Only the <b>root expression</b> is scoped (the table right after FROM, or the
 * UPDATE target), so joins, subqueries, and non-tenant lookup tables (users,
 * tenants, etc.) are untouched. Tables that own a {@code tenant_id} column are
 * listed explicitly.
 *
 * <p>When no tenant is set (bootstrap superadmin), SQL is unchanged — full visibility.
 */
public class TenantStatementInspector implements StatementInspector {

    private static final Set<String> TENANT_TABLES = Set.of(
            "patients", "appointments", "encounters", "charges", "invoices",
            "payments", "prescriptions", "lab_orders", "medicine_batches",
            "medicine_catalog", "stock_transactions", "opd_queue", "rooms",
            "admissions", "enquiries", "enquiry_status_history", "consents",
            "discharge_summaries", "discharge_summary_addenda"
    );

    /** For {@code TenantWriteScopingGuardTest} to verify the entity↔table alignment. */
    static Set<String> tenantTables() {
        return TENANT_TABLES;
    }

    @Override
    public String inspect(String sql) {
        if (sql == null) {
            return sql;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        if (lower.regionMatches(true, 0, "select", 0, 6)) {
            var tenantOpt = TenantContext.getTenantId();
            return tenantOpt.isEmpty() ? sql
                    : scope(sql, lower, tenantOpt.get().toString(), afterKeyword(lower, 6, " from "), null, false);
        }
        if (lower.regionMatches(true, 0, "update", 0, 6)) {
            var tenantOpt = TenantContext.getTenantId();
            return tenantOpt.isEmpty() ? sql
                    : scope(sql, lower, tenantOpt.get().toString(), skipWhitespace(lower, 6), "set", true);
        }
        if (lower.regionMatches(true, 0, "delete", 0, 6)) {
            var tenantOpt = TenantContext.getTenantId();
            return tenantOpt.isEmpty() ? sql
                    : scope(sql, lower, tenantOpt.get().toString(), afterKeyword(lower, 6, " from "), null, true);
        }
        if (lower.regionMatches(true, 0, "insert", 0, 6)) {
            return guardInsert(sql, lower);
        }
        return sql;
    }

    private int afterKeyword(String lower, int from, String keyword) {
        int idx = lower.indexOf(keyword, from);
        return idx < 0 ? -1 : skipWhitespace(lower, idx + keyword.length());
    }

    private int skipWhitespace(String sql, int from) {
        int i = from;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
        return i;
    }

    /**
     * Locate the root table token, derive the alias, and splice
     * {@code <alias>.tenant_id = '<tenant>'} into the top-level WHERE.
     *
     * @param tableStart        index of the first character of the root table name
     *                          (already whitespace-skipped), or -1 when absent.
     * @param afterTableKeyword non-null for UPDATE, whose next word is SET — a token
     *                          other than it is a real alias; for SELECT/DELETE a
     *                          keyword (where/using/returning) means "no alias".
     */
    private String scope(String sql, String lower, String tenantIdValue, int tableStart,
                         String afterTableKeyword, boolean isWrite) {
        if (tableStart < 0) {
            return sql;
        }
        int tableEnd = skipWord(sql, tableStart);
        String table = lower.substring(tableStart, tableEnd).toLowerCase(Locale.ROOT);
        if (!TENANT_TABLES.contains(table)) {
            return sql;
        }
        int aliasStart = skipWhitespace(sql, tableEnd);
        int aliasEnd = skipWord(sql, aliasStart);
        String aliasCandidate = aliasEnd > aliasStart
                ? lower.substring(aliasStart, aliasEnd) : "";
        boolean aliasIsTable = afterTableKeyword != null
                ? afterTableKeyword.equals(aliasCandidate)
                : aliasCandidate.isEmpty() || RESERVED_SPANNERS.contains(aliasCandidate);
        // When the token is not an alias (a keyword like WHERE/SET, or nothing), the
        // alias span collapses to the table itself so scan/splice never consume it.
        String alias = aliasIsTable ? table : aliasCandidate;
        if (aliasIsTable) {
            aliasEnd = tableEnd;
        }

        // Find the end of the alias — next keyword at top level. A keyword that only
        // appears inside parentheses or a string literal does not terminate the scan.
        int insertAt = aliasEnd;
        int whereIdx = indexOfKeyword(lower, insertAt, " where");
        int groupIdx = indexOfKeyword(lower, insertAt, " group ");
        int orderIdx = indexOfKeyword(lower, insertAt, " order ");
        int limitIdx = indexOfKeyword(lower, insertAt, " limit");
        int fetchIdx = indexOfKeyword(lower, insertAt, " fetch ");
        int unionIdx = indexOfKeyword(lower, insertAt, " union ");
        int joinIdx = indexOfKeyword(lower, insertAt, " join ");
        int offsetIdx = indexOfKeyword(lower, insertAt, " offset");
        int usingIdx = isWrite ? indexOfKeyword(lower, insertAt, " using ") : -1;
        int returningIdx = isWrite ? indexOfKeyword(lower, insertAt, " returning") : -1;
        int min = Integer.MAX_VALUE;
        for (int i : new int[]{whereIdx, groupIdx, orderIdx, limitIdx, fetchIdx,
                unionIdx, joinIdx, offsetIdx, usingIdx, returningIdx}) {
            if (i >= 0 && i < min) min = i;
        }
        if (min == Integer.MAX_VALUE) min = sql.length();

        // Escape single quotes in tenantIdValue to prevent SQL injection
        String safeTenantId = tenantIdValue.replace("'", "''");
        String predicate = alias + ".tenant_id = '" + safeTenantId + "'";
        StringBuilder sb = new StringBuilder(sql.length() + 40);
        sb.append(sql, 0, aliasEnd);
        if (whereIdx >= 0 && whereIdx == min) {
            // Preserve the span up to and including " where" (for UPDATE that is the
            // whole SET clause), then fold the tenant predicate in first.
            sb.append(sql, aliasEnd, whereIdx + 6).append(" ").append(predicate).append(" and");
            sb.append(sql, whereIdx + 6, sql.length());
        } else {
            sb.append(sql, aliasEnd, min).append(" where ").append(predicate);
            sb.append(sql, min, sql.length());
        }
        return sb.toString();
    }

    /** Words that follow a table/alias must never be mistaken for an alias token. */
    private static final Set<String> RESERVED_SPANNERS = Set.of(
            "where", "using", "returning", "set", "group", "order", "limit",
            "offset", "union", "join", "on");

    private int skipWord(String sql, int from) {
        int i = from;
        while (i < sql.length() && !Character.isWhitespace(sql.charAt(i))) i++;
        return i;
    }

    private int indexOfKeyword(String lower, int from, String kw) {
        // A keyword is top-level only if it is outside any single-quoted literal and
        // not inside a parenthesised subquery. Walk once tracking quote state ('' is
        // an escaped literal quote — two flips return the same state, correctly).
        boolean inQuote = false;
        int parenDepth = 0;
        for (int i = from; i + kw.length() <= lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                continue;
            }
            if (inQuote) {
                continue;
            }
            if (c == '(') {
                parenDepth++;
                continue;
            }
            if (c == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                continue;
            }
            if (parenDepth == 0 && lower.startsWith(kw, i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The INSERT guard: a tenant-scoped table whose column list omits {@code tenant_id}
     * would write a row no tenant-scoped read can ever reach — refuse instead.
     */
    private String guardInsert(String sql, String lower) {
        var tenantOpt = TenantContext.getTenantId();
        if (tenantOpt.isEmpty()) {
            return sql;
        }
        int intoIdx = lower.indexOf(" into ", 6);
        if (intoIdx < 0) {
            return sql;
        }
        int tableStart = skipWhitespace(lower, intoIdx + " into ".length());
        int tableEnd = skipWord(lower, tableStart);
        String table = lower.substring(tableStart, tableEnd);
        if (!TENANT_TABLES.contains(table)) {
            return sql;
        }
        int columnsOpen = lower.indexOf('(', tableEnd);
        int columnsClose = columnsOpen < 0 ? -1 : lower.indexOf(')', columnsOpen);
        boolean namesTenant = columnsOpen >= 0 && columnsClose > columnsOpen
                && lower.substring(columnsOpen + 1, columnsClose)
                        .matches("(?s).*\\btenant_id\\b.*");
        if (!namesTenant) {
            throw new IllegalStateException("INSERT into tenant-scoped table '" + table
                    + "' carries no tenant_id column. The row would be written outside every "
                    + "tenant's visible scope, so it is refused rather than silently stored. "
                    + "Writes must travel a TenantOwned entity (TenantEntityListener stamps "
                    + "tenantId) or name the column explicitly in native SQL.");
        }
        return sql;
    }
}
