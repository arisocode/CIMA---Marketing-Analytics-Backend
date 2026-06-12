package com.cimaxis.demo.security;

import java.io.IOException;
import java.util.List;

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
/**
 * Filtro de autenticación para el ecosistema CIMA.
 * Confía exclusivamente en las cabeceras validadas y propagadas por el API Gateway (KrakenD).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Extraer las cabeceras validadas por el Gateway
        String gatewaySub = request.getHeader("X-User-Sub");
        String gatewayRole = request.getHeader("X-User-Role");

        if (gatewaySub != null && gatewayRole != null) {
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