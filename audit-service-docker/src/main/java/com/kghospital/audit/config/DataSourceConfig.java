package com.kghospital.audit.config;

import com.kghospital.audit.tenant.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url-template}")
    private String urlTemplate;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${app.tenants}")
    private List<String> tenants;

    @Bean
    @Primary
    public DataSource dataSource() {
        Map<Object, Object> dataSources = new HashMap<>();

        for (String tenantId : tenants) {
            // 1. Resolve the JDBC URL for this specific tenant
            String jdbcUrl = urlTemplate.replace("{tenant}", tenantId);

            // 2. Programmatically execute Flyway migrations directly on the connection string.
            // Decoupling from the HikariDataSource object prevents premature pool validation issues.
            try {
                Flyway flyway = Flyway.configure()
                        .dataSource(jdbcUrl, username, password) // Pass connection strings directly
                        .locations("classpath:db/migration/tenant") // Directory of your SQL migration scripts
                        .baselineOnMigrate(true)
                        .load();

                flyway.migrate();
            } catch (Exception e) {
                throw new RuntimeException("CRITICAL: Database migration failed for Tenant: " + tenantId + ". Halting application startup.", e);
            }

            // 3. Create the Hikari connection pool for the tenant
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setMaximumPoolSize(5);
            ds.setPoolName("pool-" + tenantId);

            dataSources.put(tenantId, ds);
        }

        // 4. Configure the Routing Data Source with the migrated pools
        TenantRoutingDataSource routing = new TenantRoutingDataSource();
        routing.setTargetDataSources(dataSources);
        routing.setDefaultTargetDataSource(dataSources.values().iterator().next());
        routing.afterPropertiesSet();

        return routing;
    }
}