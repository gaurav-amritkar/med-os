package com.medos.config;

import com.medos.util.EncryptionUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes PII encryption at application startup.
 */
@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    @Value("${medos.security.pii-encryption-key:}")
    private String piiEncryptionKey;

    @PostConstruct
    public void init() {
        if (piiEncryptionKey == null || piiEncryptionKey.isBlank()) {
            throw new IllegalStateException("PII encryption key (medos.security.pii-encryption-key) is required. Generate with: openssl rand -base64 32");
        }
        EncryptionUtil.init(piiEncryptionKey);
    }
}