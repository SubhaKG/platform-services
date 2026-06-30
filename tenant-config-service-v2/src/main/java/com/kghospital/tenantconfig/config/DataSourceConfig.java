package com.kghospital.tenantconfig.config;

import com.kghospital.tenantconfig.tenant.TenantRoutingDataSource;
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
 * PLAT-005 §6.3 — "Key catalogue is platform-wide — shared schema, not
 * tenant-scoped. Config values are tenant-scoped — stored in the
 * tenant DB per hospital_id." This is the FIRST service in the platform
 * needing two genuinely separate DataSource beans: one tenant-routed
 * (config_values, config_audit_log — same pattern as every other
 * service) and one single shared connection (config_key_catalogue,
 * the ONE catalogue every tenant reads from).
 */
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

    @Value("${spring.platform-datasource.url}")
    private String platformUrl;
    @Value("${spring.platform-datasource.username}")
    private String platformUsername;
    @Value("${spring.platform-datasource.password}")
    private String platformPassword;

    /** Tenant-routed — config_values, config_audit_log */
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

    /** Single shared connection — config_key_catalogue */
    @Bean
    @Qualifier("platformDataSource")
    public DataSource platformDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(platformUrl);
        ds.setUsername(platformUsername);
        ds.setPassword(platformPassword);
        ds.setMaximumPoolSize(5);
        ds.setPoolName("pool-platform-catalogue");
        return ds;
    }
}
