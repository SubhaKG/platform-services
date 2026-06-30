package com.kghospital.workflow.config;

import com.kghospital.workflow.tenant.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.*;

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
}
