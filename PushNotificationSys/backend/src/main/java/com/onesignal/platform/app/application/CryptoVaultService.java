package com.onesignal.platform.app.application;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class CryptoVaultService {

    @Value("${app.crypto.master-key:k4P8q3Z1y7W2v9T0x5R8u1O3m6L9j2H5g7E0b3D6a8=}")
    private String masterKeyConfig;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int GCM_IV_LENGTH = 12; // 96-bit IV
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(masterKeyConfig.trim());
            } catch (IllegalArgumentException e) {
                keyBytes = masterKeyConfig.getBytes(StandardCharsets.UTF_8);
            }

            // Ensure exactly 256 bits (32 bytes)
            if (keyBytes.length != 32) {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                keyBytes = sha256.digest(keyBytes);
            }

            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("CryptoVaultService initialized with AES-256-GCM cipher.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CryptoVaultService AES key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Error encrypting payload", e);
            throw new RuntimeException("Encryption failure", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null) return null;
        try {
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedBase64);
            if (cipherMessage.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted data too short");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting payload", e);
            throw new RuntimeException("Decryption failure or payload tampered", e);
        }
    }
}
