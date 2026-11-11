package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushCryptoService {

    private static final String CURVE_NAME = "prime256v1"; // secp256r1 / NIST P-256
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private final SecureRandom secureRandom = new SecureRandom();

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    /**
     * Signs a VAPID JWT token according to RFC 8292 using ES256 (ECDSA P-256).
     */
    public String createVapidToken(String endpointUrl, String vapidPrivateKeyBase64, String subject) {
        try {
            URI uri = URI.create(endpointUrl);
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");

            Map<String, Object> header = Map.of(
                    "typ", "JWT",
                    "alg", "ES256"
            );

            long now = System.currentTimeMillis() / 1000L;
            Map<String, Object> payload = Map.of(
                    "aud", audience,
                    "exp", now + (12 * 3600), // 12 hours
                    "sub", (subject != null && !subject.isBlank()) ? subject : "mailto:admin@pushnotify.local"
            );

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;

            // Load private key
            byte[] privateKeyBytes = Base64.getUrlDecoder().decode(vapidPrivateKeyBase64);
            BigInteger s = new BigInteger(1, privateKeyBytes);

            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            ECPrivateKeySpec privSpec = new ECPrivateKeySpec(s, ecSpec);
            PrivateKey privateKey = kf.generatePrivate(privSpec);

            Signature signer = Signature.getInstance("SHA256withECDSA", "BC");
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] derSignature = signer.sign();

            // Convert DER signature to raw (r || s) 64 bytes
            byte[] rawSignature = derToRawEs256(derSignature);
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate VAPID JWT: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts plaintext message payload according to RFC 8291 (aes128gcm).
     */
    public byte[] encryptPayload(byte[] plaintext, String clientP256dhBase64, String clientAuthBase64) {
        try {
            byte[] clientPublicKeyBytes = Base64.getUrlDecoder().decode(clientP256dhBase64);
            byte[] clientAuthBytes = Base64.getUrlDecoder().decode(clientAuthBase64);

            // 1. Generate local ephemeral keypair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            kpg.initialize(ecSpec, secureRandom);
            KeyPair localKeyPair = kpg.generateKeyPair();

            // 2. Decode client public key
            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            ECPoint clientPoint = ecSpec.getCurve().decodePoint(clientPublicKeyBytes);
            ECPublicKeySpec clientPubSpec = new ECPublicKeySpec(clientPoint, ecSpec);
            PublicKey clientPublicKey = kf.generatePublic(clientPubSpec);

            // 3. Perform ECDH to get shared secret
            KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
            ka.init(localKeyPair.getPrivate());
            ka.doPhase(clientPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // 4. Generate random 16-byte salt
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);

            // 5. Get uncompressed local public key (65 bytes)
            org.bouncycastle.jce.interfaces.ECPublicKey localEcPub =
                    (org.bouncycastle.jce.interfaces.ECPublicKey) localKeyPair.getPublic();
            byte[] localPublicKeyBytes = localEcPub.getQ().getEncoded(false);

            // 6. HKDF Key Derivation (RFC 8291)
            byte[] prk = hkdfExtract(clientAuthBytes, sharedSecret);
            byte[] keyInfo = buildInfo("aesgcm", clientPublicKeyBytes, localPublicKeyBytes);
            byte[] ikm = hkdfExpand(prk, keyInfo, 32);

            byte[] prkContent = hkdfExtract(salt, ikm);
            byte[] cekInfo = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
            byte[] cek = hkdfExpand(prkContent, cekInfo, 16);

            byte[] nonceInfo = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
            byte[] nonce = hkdfExpand(prkContent, nonceInfo, 12);

            // 7. Add padding delimiter (0x02 for final record)
            byte[] paddedPlaintext = new byte[plaintext.length + 1];
            System.arraycopy(plaintext, 0, paddedPlaintext, 0, plaintext.length);
            paddedPlaintext[paddedPlaintext.length - 1] = 0x02;

            // 8. Encrypt with AES/GCM/NoPadding
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(paddedPlaintext);

            // 9. Build RFC 8291 Header:
            // Salt (16 bytes) || Record Size (4 bytes = 4096) || KeyIdLen (1 byte = 65) || Local PubKey (65 bytes) || Ciphertext
            int recordSize = 4096;
            ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 1 + localPublicKeyBytes.length + ciphertext.length);
            buffer.put(salt);
            buffer.putInt(recordSize);
            buffer.put((byte) localPublicKeyBytes.length);
            buffer.put(localPublicKeyBytes);
            buffer.put(ciphertext);

            return buffer.array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt Web Push payload: " + e.getMessage(), e);
        }
    }

    private byte[] derToRawEs256(byte[] der) {
        // DER format: 0x30 || len || 0x02 || rLen || r || 0x02 || sLen || s
        int offset = 2; // skip 0x30, len
        if ((der[1] & 0x80) != 0) {
            offset += (der[1] & 0x0F);
        }

        offset++; // skip 0x02
        int rLen = der[offset++];
        int rOffset = offset;
        offset += rLen;

        offset++; // skip 0x02
        int sLen = der[offset++];
        int sOffset = offset;

        byte[] raw = new byte[64];
        copyPadded(der, rOffset, rLen, raw, 0, 32);
        copyPadded(der, sOffset, sLen, raw, 32, 32);
        return raw;
    }

    private void copyPadded(byte[] src, int srcOffset, int srcLen, byte[] dest, int destOffset, int targetLen) {
        int copyLen = Math.min(srcLen, targetLen);
        int srcStart = srcOffset + Math.max(0, srcLen - targetLen);
        int destStart = destOffset + Math.max(0, targetLen - srcLen);
        System.arraycopy(src, srcStart, dest, destStart, copyLen);
    }

    private byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256", "BC");
        mac.init(new SecretKeySpec(salt != null && salt.length > 0 ? salt : new byte[32], "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256", "BC");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        ByteBuffer result = ByteBuffer.allocate(length);
        byte[] t = new byte[0];
        byte counter = 1;

        while (result.hasRemaining()) {
            mac.update(t);
            mac.update(info);
            mac.update(counter);
            t = mac.doFinal();
            int toCopy = Math.min(t.length, result.remaining());
            result.put(t, 0, toCopy);
            counter++;
        }
        return result.array();
    }

    private byte[] buildInfo(String type, byte[] clientPub, byte[] localPub) {
        byte[] typeBytes = ("WebPush: info\0").getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(typeBytes.length + clientPub.length + localPub.length);
        buffer.put(typeBytes);
        buffer.put(clientPub);
        buffer.put(localPub);
        return buffer.array();
    }
}
