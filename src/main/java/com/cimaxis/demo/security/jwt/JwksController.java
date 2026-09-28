package com.cimaxis.demo.security.jwt;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint JWKS (RFC 7517) para verificación M2M por otros microservicios.
 */
@RestController
@RequestMapping("/api/v1/.well-known")
public class JwksController {

    private final ServiceJwtSigner signer;

    public JwksController(ServiceJwtSigner signer) {
        this.signer = signer;
    }

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getJwks() {
        return signer.getJwksDocument();
    }
}
