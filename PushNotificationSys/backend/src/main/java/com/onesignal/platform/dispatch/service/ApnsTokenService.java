package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ApnsTokenService {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final long TOKEN_CACHE_DURATION_MS = 45 * 60 * 1000L; // 45 minutes

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    private static class CachedToken {
        final String token;
        final long createdAt;

        CachedToken(String token) {
            this.token = token;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > TOKEN_CACHE_DURATION_MS;
        }
    }

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Generates or retrieves a cached APNs authentication JWT signed with ES256 (ECDSA P-256).
     */
    public String getApnsAuthToken(String p8PrivateKeyContent, String keyId, String teamId) {
        String cacheKey = teamId + ":" + keyId;
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.token;
        }

        String freshToken = generateToken(p8PrivateKeyContent, keyId, teamId);
        tokenCache.put(cacheKey, new CachedToken(freshToken));
        return freshToken;
    }

    public String generateToken(String p8PrivateKeyContent, String keyId, String teamId) {
        try {
            PrivateKey privateKey = parsePrivateKey(p8PrivateKeyContent);

            Map<String, Object> header = Map.of(
                    "alg", "ES256",
                    "kid", keyId
            );

            long nowSeconds = System.currentTimeMillis() / 1000L;
            Map<String, Object> payload = Map.of(
                    "iss", teamId,
                    "iat", nowSeconds
            );

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] derSignature = signature.sign();

            byte[] rawSignature = derToRawEs256(derSignature);
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            log.error("Failed to generate APNs JWT for team {} and key {}: {}", teamId, keyId, e.getMessage());
            throw new RuntimeException("Failed to generate APNs provider authentication token: " + e.getMessage(), e);
        }
    }

    public PrivateKey parsePrivateKey(String p8Content) throws Exception {
        if (p8Content == null || p8Content.isBlank()) {
            throw new IllegalArgumentException("APNs private key content cannot be empty");
        }

        String cleaned = p8Content.trim();

        // 1. Try BouncyCastle PEMParser
        try (PEMParser parser = new PEMParser(new StringReader(cleaned))) {
            Object parsedObj = parser.readObject();
            if (parsedObj != null) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                if (parsedObj instanceof PrivateKeyInfo pki) {
                    return converter.getPrivateKey(pki);
                } else if (parsedObj instanceof PEMKeyPair kp) {
                    return converter.getPrivateKey(kp.getPrivateKeyInfo());
                }
            }
        } catch (Exception ignored) {
            // Fall through to raw base64 extraction
        }

        // 2. Strip any headers, footers, whitespace and parse PKCS#8
        String rawBase64 = cleaned
                .replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s+", "");

        byte[] derBytes = Base64.getDecoder().decode(rawBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        return kf.generatePrivate(keySpec);
    }

    private byte[] derToRawEs256(byte[] derSignature) {
        int offset = 3;
        int rLength = derSignature[offset++];
        byte[] r = new byte[32];
        if (rLength > 32) {
            System.arraycopy(derSignature, offset + (rLength - 32), r, 0, 32);
        } else {
            System.arraycopy(derSignature, offset, r, 32 - rLength, rLength);
        }
        offset += rLength;

        offset++; // skip 0x02 marker for s
        int sLength = derSignature[offset++];
        byte[] s = new byte[32];
        if (sLength > 32) {
            System.arraycopy(derSignature, offset + (sLength - 32), s, 0, 32);
        } else {
            System.arraycopy(derSignature, offset, s, 32 - sLength, sLength);
        }

        byte[] raw = new byte[64];
        System.arraycopy(r, 0, raw, 0, 32);
        System.arraycopy(s, 0, raw, 32, 32);
        return raw;
    }
}
