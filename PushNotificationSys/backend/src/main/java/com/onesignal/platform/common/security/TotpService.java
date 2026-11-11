package com.onesignal.platform.common.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class TotpService {

    public static final int TIME_STEP_SECONDS = 30;
    public static final int CODE_DIGITS = 6;
    private static final int MODULO = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String HMAC_ALGO = "HmacSHA1";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 20-byte (160-bit) cryptographically secure Base32 secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[20];
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Generates an RFC-compliant otpauth:// URI for authenticator applications.
     */
    public String generateOtpAuthUri(String issuer, String accountName, String base32Secret) {
        String cleanIssuer = issuer.trim();
        String cleanAccount = accountName.trim();
        String encodedIssuer = URLEncoder.encode(cleanIssuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(cleanAccount, StandardCharsets.UTF_8).replace("+", "%20");
        String label = encodedIssuer + ":" + encodedAccount;

        return String.format(
                "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                label,
                base32Secret.trim().toUpperCase(),
                encodedIssuer,
                CODE_DIGITS,
                TIME_STEP_SECONDS
        );
    }

    /**
     * Generates a Base64-encoded Data URL representing a PNG QR code.
     */
    public String generateQrCodeDataUrl(String otpauthUri, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpauthUri, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            String base64Image = Base64.getEncoder().encodeToString(pngOut.toByteArray());
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            log.error("Failed to generate QR code for URI: {}", e.getMessage());
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Verifies a 6-digit TOTP code with +/- 1 time step drift tolerance (total 90s window).
     */
    public boolean verifyCode(String base32Secret, String candidateCode, Instant timestamp) {
        if (base32Secret == null || candidateCode == null) {
            return false;
        }

        String cleanCode = candidateCode.trim().replaceAll("[\\s-]+", "");
        if (cleanCode.length() != CODE_DIGITS || !cleanCode.chars().allMatch(Character::isDigit)) {
            return false;
        }

        byte[] secretBytes;
        try {
            secretBytes = decodeBase32(base32Secret);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Base32 secret during TOTP verification: {}", e.getMessage());
            return false;
        }

        long currentStep = timestamp.getEpochSecond() / TIME_STEP_SECONDS;

        // Check window: 3 steps back (~90-120s past) to 1 step forward (~30s future)
        // Accommodates human typing latency and mobile device time drifts
        for (long step = currentStep - 3; step <= currentStep + 1; step++) {
            String expected = generateCodeForStep(secretBytes, step);
            if (expected.equals(cleanCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies against current system time.
     */
    public boolean verifyCode(String base32Secret, String candidateCode) {
        return verifyCode(base32Secret, candidateCode, Instant.now());
    }

    /**
     * Generates the 6-digit code for a specific timestamp (primarily for testing and validation).
     */
    public String generateCode(String base32Secret, Instant timestamp) {
        byte[] secretBytes = decodeBase32(base32Secret);
        long step = timestamp.getEpochSecond() / TIME_STEP_SECONDS;
        return generateCodeForStep(secretBytes, step);
    }

    /**
     * Generates N emergency backup recovery codes (format: XXXX-XXXX).
     */
    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Omits confusing chars (0, O, 1, I)
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private String generateCodeForStep(byte[] secretBytes, long step) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % MODULO;
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA1 TOTP", e);
        }
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }

        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }

        return sb.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_ALPHABET.indexOf(c);
            if (val == -1) {
                throw new IllegalArgumentException("Illegal character in Base32 string: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }

        return out.toByteArray();
    }
}
