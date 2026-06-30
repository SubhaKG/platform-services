package com.kghospital.integrationregistry.config;

import com.kghospital.integrationregistry.iam.IamAuthenticationFilter;
import com.kghospital.integrationregistry.iam.IamIntrospectionClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** §7.3 — "The lookup endpoint... MUST NOT be publicly accessible." */
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
                .anyRequest().authenticated()
            )
            .addFilterBefore(new IamAuthenticationFilter(iamClient),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
