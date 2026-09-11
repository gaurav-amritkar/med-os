package com.medos.config;

import com.medos.security.TenantContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.Locale;
import java.util.Set;

/**
 * Rewrites SQL at prepare time to scope tenant-owned tables to the current tenant.
 *
 * <p>Injects {@code <alias>.tenant_id = ?} into the root FROM clause of SELECT
 * statements when {@link TenantContext} holds a tenant. The binder happens before
 * execution, so it is robust regardless of Hibernate session/filter wiring.
 *
 * <p>Only the <b>root entity</b> FROM is scoped (the first FROM in the statement),
 * so joins, subqueries, and non-tenant lookup tables (users, tenants, etc.) are
 * untouched. Tables that own a {@code tenant_id} column are listed explicitly.
 *
 * <p>When no tenant is set (bootstrap superadmin), SQL is unchanged — full visibility.
 */
public class TenantStatementInspector implements StatementInspector {

    private static final Set<String> TENANT_TABLES = Set.of(
            "patients", "appointments", "encounters", "charges", "invoices",
            "payments", "prescriptions", "lab_orders", "medicine_batches",
            "medicine_catalog", "stock_transactions", "opd_queue", "rooms",
            "admissions"
    );

    @Override
    public String inspect(String sql) {
        if (sql == null || !sql.regionMatches(true, 0, "select", 0, 6)) {
            return sql;
        }
        var tenantOpt = TenantContext.getTenantId();
        if (tenantOpt.isEmpty()) {
            return sql;
        }
        String tenantIdValue = tenantOpt.get().toString();
        String lower = sql.toLowerCase(Locale.ROOT);
        int fromIdx = lower.indexOf(" from ");
        if (fromIdx < 0) {
            return sql;
        }
        // Find the table token right after FROM
        int afterFrom = fromIdx + 6;
        while (afterFrom < sql.length() && Character.isWhitespace(sql.charAt(afterFrom))) afterFrom++;
        int tableStart = afterFrom;
        while (afterFrom < sql.length() && !Character.isWhitespace(sql.charAt(afterFrom))) afterFrom++;
        String table = sql.substring(tableStart, afterFrom);
        if (!TENANT_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
            return sql; // root entity is not tenant-scoped — leave it
        }
        // Table alias = token after table name (may be absent)
        int aliasStart = afterFrom;
        while (aliasStart < sql.length() && Character.isWhitespace(sql.charAt(aliasStart))) aliasStart++;
        int aliasEnd = aliasStart;
        while (aliasEnd < sql.length() && !Character.isWhitespace(sql.charAt(aliasEnd))) aliasEnd++;
        String alias = aliasEnd > aliasStart ? sql.substring(aliasStart, aliasEnd) : table;

        // Find the end of the FROM clause — next major keyword at top level
        int insertAt = aliasEnd;
        // Look ahead for WHERE/GROUP/ORDER/LIMIT/FETCH/JOIN/UNION — but only if they
        // appear before any '(' (subquery) — simple here: stop at the first keyword.
        int whereIdx = indexOfKeyword(lower, insertAt, " where");
        int groupIdx = indexOfKeyword(lower, insertAt, " group ");
        int orderIdx = indexOfKeyword(lower, insertAt, " order ");
        int limitIdx = indexOfKeyword(lower, insertAt, " limit");
        int fetchIdx = indexOfKeyword(lower, insertAt, " fetch ");
        int unionIdx = indexOfKeyword(lower, insertAt, " union ");
        int joinIdx = indexOfKeyword(lower, insertAt, " join ");
        int offsetIdx = indexOfKeyword(lower, insertAt, " offset");
        int min = Integer.MAX_VALUE;
        for (int i : new int[]{whereIdx, groupIdx, orderIdx, limitIdx, fetchIdx, unionIdx, joinIdx, offsetIdx}) {
            if (i >= 0 && i < min) min = i;
        }
        if (min == Integer.MAX_VALUE) min = sql.length();

        String predicate = alias + ".tenant_id = '" + tenantIdValue + "'";
        StringBuilder sb = new StringBuilder(sql.length() + 40);
        sb.append(sql, 0, aliasEnd);
        if (whereIdx >= 0 && whereIdx == min) {
            // Already has WHERE — fold in with AND (skip the leading space + "where")
            sb.append(" where ").append(predicate).append(" and");
            sb.append(sql, whereIdx + 6, sql.length());
        } else {
            sb.append(" where ").append(predicate);
            sb.append(sql, min, sql.length());
        }
        return sb.toString();
    }

    private int indexOfKeyword(String lower, int from, String kw) {
        int i = lower.indexOf(kw, from);
        // Reject if inside a parenthesised subquery
        while (i >= 0) {
            String prefix = lower.substring(from, i);
            if (prefix.indexOf('(') < 0) return i;
            i = lower.indexOf(kw, i + 1);
        }
        return -1;
    }
}