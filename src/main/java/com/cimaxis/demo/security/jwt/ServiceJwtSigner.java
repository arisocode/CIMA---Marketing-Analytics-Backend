package com.cimaxis.demo.security.jwt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Proveedor de par de claves RSA y generador de aserciones JWT RS256 para M2M.
 */
@Component
public class ServiceJwtSigner {

    private static final Logger log = LoggerFactory.getLogger(ServiceJwtSigner.class);

    private final String serviceName;
    private final String keyId;
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final JsonMapper jsonMapper;

    @Autowired
    public ServiceJwtSigner(
            @Value("${cimaxis.jwt.service-name:crm-marketing}") String serviceName,
            @Value("${cimaxis.jwt.kid:marketing-service-rsa-1}") String keyId,
            @Value("${cimaxis.jwt.private-key:}") String privateKeyPem,
            @Value("${cimaxis.jwt.public-key:}") String publicKeyPem,
            JsonMapper jsonMapper) {
        this.serviceName = serviceName;
        this.keyId = keyId;
        this.jsonMapper = jsonMapper;

        KeyPair resolvedPair = resolveKeyPair(privateKeyPem, publicKeyPem);
        this.publicKey = (RSAPublicKey) resolvedPair.getPublic();
        this.privateKey = (RSAPrivateKey) resolvedPair.getPrivate();
    }

    public ServiceJwtSigner(String serviceName, String keyId, JsonMapper jsonMapper) {
        this(serviceName, keyId, "", "", jsonMapper);
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

    private static KeyPair resolveKeyPair(String privateKeyPem, String publicKeyPem) {
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            return loadStaticKeyPair(privateKeyPem.trim(), publicKeyPem != null ? publicKeyPem.trim() : "");
        }
        log.warn("SERVICE_JWT_PRIVATE_KEY no configurada; generando par RSA efímero (solo recomendado en desarrollo)");
        return generateEphemeralKeyPair();
    }

    private static KeyPair loadStaticKeyPair(String privateKeyPem, String publicKeyPem) {
        try {
            RSAPrivateKey privKey = parsePrivateKey(privateKeyPem);
            RSAPublicKey pubKey;
            if (!publicKeyPem.isBlank()) {
                pubKey = parsePublicKey(publicKeyPem);
            } else if (privKey instanceof RSAPrivateCrtKey crtKey) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                pubKey = (RSAPublicKey) keyFactory.generatePublic(
                        new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            } else {
                throw new IllegalStateException("Se requiere SERVICE_JWT_PUBLIC_KEY o una clave privada CRT");
            }
            return new KeyPair(pubKey, privKey);
        } catch (Exception e) {
            throw new IllegalStateException("Error al cargar par de claves RSA estático desde configuración", e);
        }
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo RSA no soportado", e);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                          .replace("-----END PRIVATE KEY-----", "")
                          .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                          .replace("-----END PUBLIC KEY-----", "")
                          .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
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
