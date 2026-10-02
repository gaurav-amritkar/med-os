package com.medos.config;

import com.medos.security.TenantKeyHolder;
import com.medos.security.TenantKeyStore;
import com.medos.util.EncryptionUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Initialises PII encryption at application startup.
 *
 * <p>Two objects are wired here. {@link TenantKeyHolder} owns the key-encryption key
 * and is the only component that touches it. {@link EncryptionUtil} is the JPA
 * converter used by all nine encrypted columns; Hibernate instantiates it, so it
 * cannot be given the holder by constructor and instead reads it from the holder's
 * static accessor.
 */
@Configuration
@RequiredArgsConstructor
@EnableTransactionManagement
public class EncryptionConfig {

    @Value("${medos.security.pii-encryption-key:}")
    private String piiEncryptionKey;

    private final TenantKeyStore tenantKeyStore;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    @PostConstruct
    public void init() {
        if (piiEncryptionKey == null || piiEncryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "PII encryption key (medos.security.pii-encryption-key) is required. "
                            + "Generate with: openssl rand -base64 32");
        }
        TenantKeyHolder.setInstance(new TenantKeyHolder(piiEncryptionKey, tenantKeyStore, txManager));
        EncryptionUtil.init(piiEncryptionKey);
    }
}
