package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code wrapped_bi_key} is a nullable BYTEA: a tenant that has a data key but has
 * never needed a name index has no index key. Reading that column must yield an
 * empty Optional, not a NullPointerException.
 *
 * <p>The failure mode this guards is subtle: Spring's RowMapper happily returns a
 * {@code null} element, {@code Stream.findFirst()} throws on a null first element,
 * and the resulting stack trace points at the key store rather than at the absent
 * column, so it reads like a database fault rather than "no index key yet".
 */
class TenantKeyStoreJdbcNullableColumnTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantKeyStoreJdbc store = new TenantKeyStoreJdbc(jdbc);
    private final UUID tenant = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @SuppressWarnings("unchecked")
    private void stubSingleRowReturningNull() {
        when(jdbc.query(eq("SELECT wrapped_bi_key FROM tenant_keys WHERE tenant_id = ?"),
                ArgumentMatchers.<RowMapper<byte[]>>any(),
                ArgumentMatchers.<Object[]>any()))
                .thenReturn(Collections.singletonList(null));
    }

    @Test
    @DisplayName("a NULL wrapped_bi_key reads as 'no index key yet', not as an error")
    void nullBiKeyIsEmptyNotAnException() {
        stubSingleRowReturningNull();

        assertThat(store.wrappedBiKeyOf(tenant)).isEmpty();
    }

    @Test
    @DisplayName("a NULL wrapped_bi_key does not stop the caller from creating one")
    void nullBiKeyCanBeCreatedAfterwards() {
        stubSingleRowReturningNull();

        assertThat(store.wrappedBiKeyOf(tenant)).isEmpty();
        assertThat(store.biKeyGenerationOf(tenant).orElse(1))
                .as("generation stays at its default until an index key is actually stored")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a present wrapped_bi_key is returned unchanged")
    void presentBiKeyIsReturned() {
        byte[] wrapped = "wrapped-index-key".getBytes();
        when(jdbc.query(eq("SELECT wrapped_bi_key FROM tenant_keys WHERE tenant_id = ?"),
                ArgumentMatchers.<RowMapper<byte[]>>any(),
                ArgumentMatchers.<Object[]>any()))
                .thenReturn(Collections.singletonList(wrapped));

        assertThat(store.wrappedBiKeyOf(tenant)).contains(wrapped);
    }

    @Test
    @DisplayName("a NULL wrapped_dek also reads as empty rather than throwing")
    void nullDekIsEmptyNotAnException() {
        when(jdbc.query(eq("SELECT wrapped_dek FROM tenant_keys WHERE tenant_id = ?"),
                ArgumentMatchers.<RowMapper<byte[]>>any(),
                ArgumentMatchers.<Object[]>any()))
                .thenReturn(Collections.singletonList(null));

        assertThat(store.wrappedDekOf(tenant)).isEmpty();
    }

    @Test
    @DisplayName("a missing tenant row reads as empty, matching an absent index key")
    void missingRowIsEmpty() {
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<byte[]>>any(),
                ArgumentMatchers.<Object[]>any()))
                .thenReturn(Collections.emptyList());

        assertThat(store.wrappedBiKeyOf(tenant)).isEmpty();
        assertThat(store.wrappedDekOf(tenant)).isEmpty();
    }
}
