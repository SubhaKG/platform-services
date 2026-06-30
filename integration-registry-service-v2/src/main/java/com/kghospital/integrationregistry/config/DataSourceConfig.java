package com.kghospital.integrationregistry.config;

import com.kghospital.integrationregistry.tenant.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PLAT-012 §7.2 — "Adapter entries are tenant-scoped (stored in the
 * tenant DB). The adapter_id and adapter_type catalogues are
 * platform-wide (shared schema)." Same dual-datasource pattern as
 * tenant-config-service for the identical reason.
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url-template}") private String urlTemplate;
    @Value("${spring.datasource.username}") private String username;
    @Value("${spring.datasource.password}") private String password;
    @Value("${app.tenants}") private List<String> tenants;

    @Value("${spring.platform-datasource.url}") private String platformUrl;
    @Value("${spring.platform-datasource.username}") private String platformUsername;
    @Value("${spring.platform-datasource.password}") private String platformPassword;

    /** Tenant-routed — adapter_entries */
    @Bean
    @Primary
    public DataSource dataSource() {
        Map<Object, Object> dataSources = new HashMap<>();
        for (String tenantId : tenants) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(urlTemplate.replace("{tenant}", tenantId));
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setMaximumPoolSize(5);
            ds.setPoolName("pool-" + tenantId);
            dataSources.put(tenantId, ds);
        }
        TenantRoutingDataSource routing = new TenantRoutingDataSource();
        routing.setTargetDataSources(dataSources);
        routing.setDefaultTargetDataSource(dataSources.values().iterator().next());
        routing.afterPropertiesSet();
        return routing;
    }

    /** Single shared connection — adapter_catalogue */
    @Bean
    @Qualifier("platformDataSource")
    public DataSource platformDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(platformUrl);
        ds.setUsername(platformUsername);
        ds.setPassword(platformPassword);
        ds.setMaximumPoolSize(5);
        ds.setPoolName("pool-platform-adapter-catalogue");
        return ds;
    }
}
