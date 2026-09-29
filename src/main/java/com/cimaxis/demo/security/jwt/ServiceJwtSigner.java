package com.cimaxis.demo.security.jwt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Proveedor de par de claves RSA y generador de aserciones JWT RS256 para M2M.
 */
@Component
public class ServiceJwtSigner {

    private final String serviceName;
    private final String keyId;
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final JsonMapper jsonMapper;

    /**
     * Constructor que usa Spring (genera el par RSA al arrancar). Sin @Autowired,
     * al haber dos constructores publicos el contexto no arranca.
     */
    @Autowired
    public ServiceJwtSigner(
            @Value("${cimaxis.jwt.service-name:crm-marketing}") String serviceName,
            @Value("${cimaxis.jwt.kid:marketing-service-rsa-1}") String keyId,
            JsonMapper jsonMapper) {
        this.serviceName = serviceName;
        this.keyId = keyId;
        this.jsonMapper = jsonMapper;

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not supported", e);
        }
    }

    public ServiceJwtSigner(String serviceName, String keyId, KeyPair keyPair, JsonMapper jsonMapper) {
        this.serviceName = serviceName;
        this.keyId = keyId;
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.jsonMapper = jsonMapper;
    }

    public Map<String, Object> getJwksDocument() {
        Map<String, Object> jwk = Map.of(
            "kty", "RSA",
            "use", "sig",
            "alg", "RS256",
            "kid", keyId,
            "n", toBase64UrlUnsigned(publicKey.getModulus()),
            "e", toBase64UrlUnsigned(publicKey.getPublicExponent())
        );
        return Map.of("keys", List.of(jwk));
    }

    public String signToken(String audience, String purpose, String bodyHash) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of(
            "alg", "RS256",
            "typ", "JWT",
            "kid", keyId
        );
        Map<String, Object> claims = Map.of(
            "iss", serviceName,
            "sub", serviceName,
            "aud", audience,
            "purpose", purpose,
            "bodyHash", bodyHash,
            "iat", now,
            "exp", now + 60
        );

        try {
            String encodedHeader = base64UrlJson(header);
            String encodedClaims = base64UrlJson(claims);
            String signingInput = encodedHeader + "." + encodedClaims;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Fallo al firmar JWT de servicio", e);
        }
    }

    private String base64UrlJson(Map<String, Object> payload) throws Exception {
        String json = jsonMapper.writeValueAsString(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String toBase64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
