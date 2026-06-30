package com.kghospital.integrationregistry.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class TenantFlywayMigrationRunner implements ApplicationRunner {

    @Value("${app.tenants}") private List<String> tenants;
    @Value("${spring.datasource.url-template}") private String urlTemplate;
    @Value("${spring.datasource.username}") private String username;
    @Value("${spring.datasource.password}") private String password;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running tenant Flyway migrations for {} tenants", tenants.size());
        List<String> failed = new ArrayList<>();
        for (String tenantId : tenants) {
            try {
                String url = urlTemplate.replace("{tenant}", tenantId);
                Flyway.configure().dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true).validateOnMigrate(true).load().migrate();
                log.info("Tenant migrated: {}", tenantId);
            } catch (Exception ex) {
                log.error("Tenant Flyway FAILED for {}: {}", tenantId, ex.getMessage());
                failed.add(tenantId);
            }
        }
        if (failed.isEmpty()) log.info("All tenant migrations completed");
        else log.error("Failed tenants: {}", failed);
    }
}
