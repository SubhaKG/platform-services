package com.kghospital.tenantconfig.config;

import com.kghospital.tenantconfig.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final TenantInterceptor tenantInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Catalogue endpoints are platform-wide (no tenant context needed);
        // value/resolve/history endpoints are tenant-scoped.
        registry.addInterceptor(tenantInterceptor)
            .addPathPatterns("/api/v1/config/**")
            .excludePathPatterns("/api/v1/config/catalogue");
    }
}
