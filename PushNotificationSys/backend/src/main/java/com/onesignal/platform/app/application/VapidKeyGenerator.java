package com.onesignal.platform.app.application;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.Base64;

@Component
public class VapidKeyGenerator {

    @Getter
    @Builder
    public static class VapidKeyPair {
        private final String publicKey;   // Base64URL uncompressed point (65 bytes)
        private final String privateKey;  // Base64URL scalar (32 bytes)
    }

    public VapidKeyPair generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            kpg.initialize(ecSpec);
            KeyPair keyPair = kpg.generateKeyPair();

            ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
            ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();

            // Encode uncompressed point (0x04 || X || Y)
            ECPoint point = pub.getW();
            byte[] x = toFixedByteArray(point.getAffineX().toByteArray(), 32);
            byte[] y = toFixedByteArray(point.getAffineY().toByteArray(), 32);

            byte[] uncompressed = new byte[65];
            uncompressed[0] = 0x04;
            System.arraycopy(x, 0, uncompressed, 1, 32);
            System.arraycopy(y, 0, uncompressed, 33, 32);

            String publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);

            // Encode private scalar (32 bytes)
            byte[] s = toFixedByteArray(priv.getS().toByteArray(), 32);
            String privateKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(s);

            return VapidKeyPair.builder()
                    .publicKey(publicKeyBase64)
                    .privateKey(privateKeyBase64)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate VAPID P-256 key pair", e);
        }
    }

    private byte[] toFixedByteArray(byte[] src, int length) {
        byte[] dest = new byte[length];
        if (src.length > length) {
            // Strip leading zero sign bit
            System.arraycopy(src, src.length - length, dest, 0, length);
        } else {
            System.arraycopy(src, 0, dest, length - src.length, src.length);
        }
        return dest;
    }
}
