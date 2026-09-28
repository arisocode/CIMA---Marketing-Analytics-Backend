package com.cimaxis.demo.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsWithoutSecretWhenSecretNotConfigured() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Sub", "sub-123");
        request.addHeader("X-User-Role", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("sub-123", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void authenticatesWhenGatewaySecretMatches() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter("secret-test-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Sub", "sub-456");
        request.addHeader("X-User-Role", "worker");
        request.addHeader("X-Gateway-Secret", "secret-test-key-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("sub-456", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void rejectsWhenGatewaySecretIsMissingOrInvalid() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter("secret-test-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Sub", "sub-attacker");
        request.addHeader("X-User-Role", "admin");
        // Missing or invalid X-Gateway-Secret
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
