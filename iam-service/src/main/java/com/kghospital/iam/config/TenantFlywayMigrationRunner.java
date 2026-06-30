package com.kghospital.iam.config;

import com.kghospital.iam.tenant.TenantContext;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PLAT-001 §4.4 — per-tenant Flyway migration runner for iam-service.
 *
 * ── WHY THIS IS A BeanFactoryPostProcessor ──
 * Runs before any singleton bean (including entityManagerFactory) is
 * instantiated, guaranteeing migrations complete before Hibernate's
 * ddl-auto=validate checks table existence.
 *
 * ── WHY A BOOTSTRAP TENANT CONTEXT IS SET BELOW ──
 * Unlike a normal HTTP request — where TenantContextFilter sets
 * TenantContext from the X-Tenant-ID header before any DB call —
 * Hibernate's OWN internal schema-validation step runs once at
 * application startup, with no HTTP request in flight at all. It asks
 * TenantRoutingDataSource for a connection to introspect sequences/
 * tables, and TenantRoutingDataSource correctly throws
 * "PLAT-001-E001: Tenant context not resolved" because, at that exact
 * moment, no tenant has been set anywhere.
 *
 * This runner sets TenantContext to the FIRST configured tenant right
 * before returning control to Spring, specifically so Hibernate's
 * bootstrap-time schema validation has *some* tenant connection to
 * validate against. This is safe because:
 *   (a) it happens once, synchronously, before any HTTP traffic exists
 *   (b) TenantContextFilter overwrites it on every real request anyway
 *   (c) all configured tenants share an IDENTICAL schema after Flyway
 *       migration, so validating against tenant #1's schema is
 *       equivalent to validating against any other tenant's schema
 *
 * ── AUTO-CREATE MISSING TENANT DATABASES ──
 * Same as audit-service's runner: connects to the server's default
 * "postgres" database first and creates the tenant database if it
 * does not already exist, since Flyway/Hibernate cannot create a
 * database, only objects inside one.
 */
@Component
public class TenantFlywayMigrationRunner implements BeanFactoryPostProcessor, EnvironmentAware, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantFlywayMigrationRunner.class);

    private static final Pattern JDBC_URL_PATTERN =
        Pattern.compile("jdbc:postgresql://([^/]+)/(.+)");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> tenants = Binder.get(environment)
            .bind("app.tenants", List.class)
            .orElse(new ArrayList<>());

        String urlTemplate = environment.getProperty("spring.datasource.url-template");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (urlTemplate == null) {
            log.warn("spring.datasource.url-template not set — skipping tenant migrations "
                + "(likely running outside the multi-tenant profile, e.g. unit tests)");
            return;
        }

        if (!urlTemplate.contains("{tenant}")) {
            String message = "CRITICAL CONFIG ERROR: spring.datasource.url-template does NOT "
                + "contain the literal placeholder '{tenant}'. Current value: [" + urlTemplate + "]. "
                + "Every configured tenant will resolve to this EXACT SAME database. "
                + "Fix application.yml, e.g.: "
                + "url-template: jdbc:postgresql://<host>:<port>/{tenant}_iam";
            log.error(message);
            throw new IllegalStateException(message);
        }

        if (tenants.isEmpty()) {
            log.warn("app.tenants is empty — no tenant databases will be migrated.");
            return;
        }

        log.info("Running Flyway migrations for {} tenants BEFORE EntityManagerFactory creation: {}",
            tenants.size(), tenants);

        List<String> failed = new ArrayList<>();
        List<String> resolvedUrls = new ArrayList<>();

        for (String tenantId : tenants) {
            String url = urlTemplate.replace("{tenant}", tenantId);

            if (resolvedUrls.contains(url)) {
                log.error("CRITICAL: tenant '{}' resolved to a URL already used by another tenant: {}",
                    tenantId, url);
                failed.add(tenantId);
                continue;
            }
            resolvedUrls.add(url);

            try {
                ensureDatabaseExists(url, username, password);

                log.info("Migrating tenant DB: tenant={} url={}", tenantId, url);

                Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(true)
                    .load();

                var result = flyway.migrate();
                log.info("Tenant {} migration complete: {} script(s) applied", tenantId, result.migrationsExecuted);

            } catch (Exception ex) {
                log.error("Flyway migration FAILED for tenant {}: {}", tenantId, ex.getMessage(), ex);
                failed.add(tenantId);
            }
        }

        if (!failed.isEmpty()) {
            log.error("Flyway migration failed for {} tenant(s): {}. "
                + "Service will start but affected tenants may have schema issues.",
                failed.size(), failed);
        } else {
            log.info("All tenant DB migrations completed successfully across {} distinct databases.",
                resolvedUrls.size());
        }

        // ── Bootstrap tenant context for Hibernate's own schema validation ──
        // Without this, AbstractSchemaValidator's internal connection request
        // throws PLAT-001-E001 because no HTTP request (and therefore no
        // TenantContextFilter) has ever run at this point in the lifecycle.
        String bootstrapTenant = tenants.stream()
            .filter(t -> !failed.contains(t))
            .findFirst()
            .orElse(null);

        if (bootstrapTenant != null) {
            TenantContext.setTenantId(bootstrapTenant);
            log.info("Bootstrap tenant context set to '{}' for Hibernate schema validation at startup. "
                + "This will be overwritten by TenantContextFilter on the first real HTTP request.",
                bootstrapTenant);
        } else {
            log.error("CRITICAL: No tenant migrated successfully — cannot set a bootstrap tenant context. "
                + "EntityManagerFactory creation will fail.");
        }
    }

    private void ensureDatabaseExists(String targetJdbcUrl, String username, String password)
            throws Exception {

        var matcher = JDBC_URL_PATTERN.matcher(targetJdbcUrl);
        if (!matcher.matches()) {
            log.warn("Could not parse JDBC URL to extract database name, skipping auto-create check: {}",
                targetJdbcUrl);
            return;
        }

        String hostAndPort = matcher.group(1);
        String dbName = matcher.group(2);

        int queryIdx = dbName.indexOf('?');
        String cleanDbName = queryIdx >= 0 ? dbName.substring(0, queryIdx) : dbName;

        String adminUrl = "jdbc:postgresql://" + hostAndPort + "/postgres";

        try (Connection conn = DriverManager.getConnection(adminUrl, username, password)) {

            boolean exists;
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '" + cleanDbName.replace("'", "''") + "'")) {
                exists = rs.next();
            }

            if (exists) {
                log.debug("Database already exists: {}", cleanDbName);
                return;
            }

            log.info("Database does not exist, creating: {}", cleanDbName);

            if (!cleanDbName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException(
                    "Refusing to auto-create database with unsafe name: " + cleanDbName);
            }

            try (Statement createStmt = conn.createStatement()) {
                createStmt.executeUpdate("CREATE DATABASE " + cleanDbName);
            }

            log.info("Database created successfully: {}", cleanDbName);

        } catch (Exception ex) {
            log.error("Failed to auto-create database '{}': {}. "
                + "If the configured user lacks CREATEDB privilege, grant it with: "
                + "ALTER USER <username> CREATEDB;", cleanDbName, ex.getMessage());
            throw ex;
        }
    }
}