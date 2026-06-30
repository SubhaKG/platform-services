package com.kghospital.iam.service.migration;

import com.kghospital.iam.tenant.TenantQuarantineRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-001 §4.4 — Programmatic Flyway migration runner.
 * Isolated failure model: one tenant failure quarantines only that tenant.
 * Other tenants are completely unaffected.
 */
@Slf4j
@Service
public class ProgrammaticMigrationRunner {

    private final TenantQuarantineRegistry quarantine;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    @Value("${app.tenants}")
    private List<String> tenants;

    @Value("${spring.datasource.url-template}")
    private String urlTemplate;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    public ProgrammaticMigrationRunner(TenantQuarantineRegistry quarantine,
                                        MeterRegistry meterRegistry) {
        this.quarantine = quarantine;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void executeMigrations() {
        log.info("Starting programmatic Flyway migration for {} tenants", tenants.size());

        for (String tenantId : tenants) {
            try {
                String url = urlTemplate.replace("{tenant}", tenantId);

                Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(true)
                    .load();

                var result = flyway.migrate();
                log.info("Migration successful: tenant={} scripts={}",
                    tenantId, result.migrationsExecuted);

                incrementCounter("iam_tenant_migrations_total", tenantId, "success");

            } catch (Exception ex) {
                // ISOLATED FAILURE — do NOT throw, do NOT affect other tenants
                log.error("CRITICAL: Migration FAILED — tenant quarantined: {} reason={}",
                    tenantId, ex.getMessage(), ex);

                quarantine.markFailed(tenantId, ex.getMessage());
                incrementCounter("iam_tenant_migrations_total", tenantId, "failure");

                // TODO: fire P0 alert to on-call team
            }
        }

        int quarantined = quarantine.getQuarantinedTenants().size();
        if (quarantined == 0) {
            log.info("All {} tenant migrations completed successfully", tenants.size());
        } else {
            log.error("{} tenant(s) quarantined: {}",
                quarantined, quarantine.getQuarantinedTenants());
        }
    }

    /**
     * §4.4.5 — Retry migration for a single quarantined tenant.
     * No service restart required.
     */
    public void retryTenantMigration(String tenantId) {
        log.info("Retrying migration for tenant: {}", tenantId);
        String url = urlTemplate.replace("{tenant}", tenantId);

        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();

        flyway.migrate();

        // Success — release from quarantine
        quarantine.release(tenantId);
        log.info("Migration retry successful, tenant released: {}", tenantId);
    }

    private void incrementCounter(String name, String tenant, String outcome) {
        String key = name + ":" + tenant + ":" + outcome;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tag("tenant", tenant)
                .tag("outcome", outcome)
                .register(meterRegistry)
        ).increment();
    }
}
