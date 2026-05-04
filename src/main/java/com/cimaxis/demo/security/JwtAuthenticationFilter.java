package com.cimaxis.demo.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                JsonNode payload = decodePayload(token);

                Integer userId = extractUserId(payload);
                String role  = extractRole(payload);

                if (userId != null && role != null) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }

                request.setAttribute("userId", userId);

            } catch (Exception e) {
                logger.warn("No se pudo procesar el JWT: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private JsonNode decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Token malformado");

        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private Integer extractUserId(JsonNode payload) {
        if (payload.has("sub")) return payload.get("sub").asInt();
        return null;
    }

    private String extractRole(JsonNode payload) {
        if (payload.has("role")) return payload.get("role").asText();
        return "USER";
    }
}