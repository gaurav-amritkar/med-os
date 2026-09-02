package com.medos;

import com.medos.modules.billing.BillingModuleConfig;
import com.medos.modules.clinical.ClinicalModuleConfig;
import com.medos.modules.pharmacy.PharmacyModuleConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Import({BillingModuleConfig.class, ClinicalModuleConfig.class, PharmacyModuleConfig.class})
@EnableCaching
@EnableAsync
@EnableTransactionManagement
public class MedOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedOsApplication.class, args);
    }
}
