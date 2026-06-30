package com.kghospital.tenantconfig.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs config_key_catalogue migrations ONCE against the single shared
 * platform database — distinct from TenantFlywayMigrationRunner, since
 * this table is not tenant-scoped per §6.3.
 */
@Slf4j
@Component
@Order(0)   // before tenant migrations
public class PlatformCatalogueMigrationRunner implements ApplicationRunner {

    @Value("${spring.platform-datasource.url}") private String url;
    @Value("${spring.platform-datasource.username}") private String username;
    @Value("${spring.platform-datasource.password}") private String password;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration/platform")
                .baselineOnMigrate(true).validateOnMigrate(true).load().migrate();
            log.info("Platform catalogue migration completed");
        } catch (Exception ex) {
            log.error("CRITICAL: Platform catalogue migration FAILED: {}", ex.getMessage(), ex);
            // Catalogue is required for ALL resolution — fail fast on startup
            throw new IllegalStateException("Cannot start without platform catalogue schema", ex);
        }
    }
}
