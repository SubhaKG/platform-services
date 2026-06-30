package com.kghospital.tenantconfig.iam;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class IamAuthenticationFilter extends OncePerRequestFilter {

    private final IamIntrospectionClient iamClient;

    public IamAuthenticationFilter(IamIntrospectionClient iamClient) {
        this.iamClient = iamClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(401, "PLAT-005-E002: Missing or invalid JWT");
            return;
        }

        try {
            IamIntrospectionClient.IntrospectionResult result =
                iamClient.introspect(authHeader.substring(7));

            if (!result.active()) {
                response.sendError(401, "PLAT-005-E002: Token is not active");
                return;
            }

            List<GrantedAuthority> authorities = result.roles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            var auth = new UsernamePasswordAuthenticationToken(result.subject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);

        } catch (Exception ex) {
            log.error("IAM introspection failed: {}", ex.getMessage());
            response.sendError(503, "PLAT-005-E002: PLAT-001 unavailable — introspection failed");
        }
    }
}
