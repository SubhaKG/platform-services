package com.kghospital.tenantconfig.config;

import com.kghospital.tenantconfig.iam.IamAuthenticationFilter;
import com.kghospital.tenantconfig.iam.IamIntrospectionClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final IamIntrospectionClient iamClient;
    public SecurityConfig(IamIntrospectionClient iamClient) { this.iamClient = iamClient; }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/config/resolve", "/api/v1/config/resolve/batch").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new IamAuthenticationFilter(iamClient),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
