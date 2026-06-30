package com.kghospital.formbuilder.config;

import com.kghospital.formbuilder.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the legacy package's own TenantInterceptor for its CPOE field-config paths. */
@Configuration
@RequiredArgsConstructor
public class LegacyWebMvcConfig implements WebMvcConfigurer {
    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/v1/cpoe/form-config/**");
    }
}
