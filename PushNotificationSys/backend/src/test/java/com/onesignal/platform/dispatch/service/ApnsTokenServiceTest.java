package com.onesignal.platform.dispatch.service;

import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.*;

class ApnsTokenServiceTest {

    private ApnsTokenService tokenService;
    private String sampleP8Pem;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = new ApnsTokenService();

        // Generate a standard EC P-256 key pair and encode as PKCS#8 PEM
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("PRIVATE KEY", kp.getPrivate().getEncoded()));
        }
        sampleP8Pem = sw.toString();
    }

    @Test
    void testParsePrivateKey_ValidPem() throws Exception {
        PrivateKey priv = tokenService.parsePrivateKey(sampleP8Pem);
        assertNotNull(priv);
        assertTrue("EC".equalsIgnoreCase(priv.getAlgorithm()) || "ECDSA".equalsIgnoreCase(priv.getAlgorithm()));
    }

    @Test
    void testGenerateToken_ProducesValidJwt() {
        String keyId = "ABC1234567";
        String teamId = "DEF9876543";

        String jwt = tokenService.generateToken(sampleP8Pem, keyId, teamId);
        assertNotNull(jwt);

        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "APNs JWT must consist of header, payload, and signature");
    }

    @Test
    void testGetApnsAuthToken_CachesTokens() {
        String keyId = "ABC1234567";
        String teamId = "DEF9876543";

        String token1 = tokenService.getApnsAuthToken(sampleP8Pem, keyId, teamId);
        String token2 = tokenService.getApnsAuthToken(sampleP8Pem, keyId, teamId);

        assertSame(token1, token2, "Token should be returned from cache within TTL");
    }
}
