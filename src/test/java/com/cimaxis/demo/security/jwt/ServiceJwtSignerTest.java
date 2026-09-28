package com.cimaxis.demo.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ServiceJwtSignerTest {

    private ServiceJwtSigner signer;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        signer = new ServiceJwtSigner("crm-marketing", "marketing-key-test", jsonMapper);
    }

    @Test
    void generatesValidJwksDocument() {
        Map<String, Object> jwks = signer.getJwksDocument();

        assertThat(jwks).containsKey("keys");
        List<?> keys = (List<?>) jwks.get("keys");
        assertThat(keys).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> key = (Map<String, Object>) keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("use")).isEqualTo("sig");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key.get("kid")).isEqualTo("marketing-key-test");
        assertThat(key.get("n")).isNotNull();
        assertThat(key.get("e")).isNotNull();
    }

    @Test
    void signsValidJwtAssertion() throws Exception {
        String token = signer.signToken("crm-media:email", "email:dispatch", "dummy-hash-123");

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        Map<String, Object> header = jsonMapper.readValue(headerJson, new TypeReference<>() {});
        assertThat(header.get("alg")).isEqualTo("RS256");
        assertThat(header.get("kid")).isEqualTo("marketing-key-test");

        String claimsJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> claims = jsonMapper.readValue(claimsJson, new TypeReference<>() {});
        assertThat(claims.get("iss")).isEqualTo("crm-marketing");
        assertThat(claims.get("sub")).isEqualTo("crm-marketing");
        assertThat(claims.get("aud")).isEqualTo("crm-media:email");
        assertThat(claims.get("purpose")).isEqualTo("email:dispatch");
        assertThat(claims.get("bodyHash")).isEqualTo("dummy-hash-123");

        java.lang.reflect.Field pubField = ServiceJwtSigner.class.getDeclaredField("publicKey");
        pubField.setAccessible(true);
        RSAPublicKey publicKey = (RSAPublicKey) pubField.get(signer);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        boolean valid = verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
        assertThat(valid).isTrue();
    }
}
