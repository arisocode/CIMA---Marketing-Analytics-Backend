package com.cimaxis.demo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String expectedGatewaySecret;

    public JwtAuthenticationFilter(
            @Value("${cimaxis.security.gateway-secret:${INTERNAL_GATEWAY_SECRET:}}")
            String expectedGatewaySecret) {
        this.expectedGatewaySecret = expectedGatewaySecret != null ? expectedGatewaySecret.trim() : "";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String gatewaySub = request.getHeader("X-User-Sub");
        String gatewayRole = request.getHeader("X-User-Role");

        if (gatewaySub != null && gatewayRole != null) {
            if (!expectedGatewaySecret.isEmpty()) {
                String providedSecret = request.getHeader("X-Gateway-Secret");
                if (providedSecret == null || !expectedGatewaySecret.equals(providedSecret)) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    gatewaySub,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + gatewayRole))
                );
            SecurityContextHolder.getContext().setAuthentication(authToken);
            request.setAttribute("userId", gatewaySub);
        }

        filterChain.doFilter(request, response);
    }
}