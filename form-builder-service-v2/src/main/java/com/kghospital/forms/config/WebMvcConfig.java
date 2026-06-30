package com.kghospital.forms.config;

import com.kghospital.forms.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the NEW package's TenantInterceptor for /api/v1/forms/** paths
 * (schema + submission endpoints). The LEGACY formbuilder package's own
 * TenantInterceptor is registered separately in LegacyWebMvcConfig for its
 * /api/v1/cpoe/forms/** paths — both packages share the same DataSource
 * bean (from forms.config.DataSourceConfig) and the same DB-level tenant
 * routing key, but resolve tenant context independently per their own
 * ThreadLocal since they're separate Java packages.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/v1/forms/**");
    }
}
