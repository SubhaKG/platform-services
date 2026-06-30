package com.kghospital.audit.config;

import com.kghospital.audit.iam.IamAuthenticationFilter;
import com.kghospital.audit.iam.IamIntrospectionClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * PLAT-002 §3.5.1 — auth via synchronous PLAT-001 introspection,
 * NOT local JWKS decode. Matches spec integration table §3.8:
 * "Auth Service (PLAT-001) — REST (sync) — Return 503 if JWT
 * introspection fails; circuit breaker active."
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final IamIntrospectionClient iamClient;

    public SecurityConfig(IamIntrospectionClient iamClient) {
        this.iamClient = iamClient;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new IamAuthenticationFilter(iamClient),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
