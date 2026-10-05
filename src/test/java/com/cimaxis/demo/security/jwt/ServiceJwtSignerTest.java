package com.cimaxis.demo.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Genera documento JWKS valido")
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
    @DisplayName("Firma asercion JWT valida verificable con su clave publica")
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

    @Test
    @DisplayName("Carga par de claves RSA estatico desde variables PEM y firma tokens validos")
    void loadsStaticRsaKeyPairFromPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        String privPem = "-----BEGIN " + "PRIVATE KEY-----\n" // gitleaks:allow
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END " + "PRIVATE KEY-----";
        String pubPem = "-----BEGIN " + "PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END " + "PUBLIC KEY-----";

        ServiceJwtSigner staticSigner = new ServiceJwtSigner(
                "crm-marketing", "static-kid", privPem, pubPem, jsonMapper);

        String token = staticSigner.signToken("crm-collab", "service:auth", "hash-abc");
        String[] parts = token.split("\\.");

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        boolean valid = verifier.verify(Base64.getUrlDecoder().decode(parts[2]));

        assertThat(valid).isTrue();
        assertThat(staticSigner.getJwksDocument()).isNotNull();
    }

    @Test
    @DisplayName("Deriva clave publica automaticamente cuando solo se provee la clave privada CRT")
    void derivesPublicKeyWhenOnlyPrivateKeyProvided() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        String privPem = "-----BEGIN " + "PRIVATE KEY-----\n" // gitleaks:allow
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END " + "PRIVATE KEY-----";

        ServiceJwtSigner staticSigner = new ServiceJwtSigner(
                "crm-marketing", "derived-kid", privPem, "", jsonMapper);

        String token = staticSigner.signToken("crm-media", "email:send", "hash-xyz");
        String[] parts = token.split("\\.");

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        boolean valid = verifier.verify(Base64.getUrlDecoder().decode(parts[2]));

        assertThat(valid).isTrue();
    }
}
