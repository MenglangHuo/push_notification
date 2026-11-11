package com.onesignal.platform.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    // RFC 6238 Appendix B Test Secret: "12345678901234567890" in ASCII
    // In RFC 4648 Base32: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private static final String RFC_TEST_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    @DisplayName("generateSecret produces valid 32-character Base32 string")
    void generateSecret_returnsValidBase32() {
        String secret1 = totpService.generateSecret();
        String secret2 = totpService.generateSecret();

        assertThat(secret1).isNotBlank().hasSize(32);
        assertThat(secret2).isNotBlank().hasSize(32);
        assertThat(secret1).isNotEqualTo(secret2);

        byte[] decoded = TotpService.decodeBase32(secret1);
        assertThat(decoded).hasSize(20);
    }

    @Test
    @DisplayName("encodeBase32 and decodeBase32 round-trip matches original bytes")
    void base32_roundTrip() {
        byte[] original = "Antigravity2026MFA!".getBytes();
        String encoded = TotpService.encodeBase32(original);
        byte[] decoded = TotpService.decodeBase32(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("generateOtpAuthUri creates standard RFC otpauth URI")
    void generateOtpAuthUri_formatsCorrectly() {
        String uri = totpService.generateOtpAuthUri("PushPlatform", "admin@pushnotify.local", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/PushPlatform:admin%40pushnotify.local?")
                .contains("secret=JBSWY3DPEHPK3PXP")
                .contains("issuer=PushPlatform")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    @DisplayName("generateQrCodeDataUrl returns a valid Base64 PNG data URL")
    void generateQrCodeDataUrl_returnsDataUrl() {
        String uri = totpService.generateOtpAuthUri("PushPlatform", "admin@pushnotify.local", "JBSWY3DPEHPK3PXP");
        String dataUrl = totpService.generateQrCodeDataUrl(uri, 200);

        assertThat(dataUrl).startsWith("data:image/png;base64,");
        assertThat(dataUrl.length()).isGreaterThan(100);
    }

    @Test
    @DisplayName("RFC 6238 standard test vectors match expected 6-digit codes")
    void rfc6238_standardTestVectors() {
        // Timestamp 59s: T = 1 -> Expected: 287082
        String code1 = totpService.generateCode(RFC_TEST_SECRET_BASE32, Instant.ofEpochSecond(59L));
        assertThat(code1).isEqualTo("287082");

        // Timestamp 1111111109s: T = 37037036 -> Expected: 081804
        String code2 = totpService.generateCode(RFC_TEST_SECRET_BASE32, Instant.ofEpochSecond(1111111109L));
        assertThat(code2).isEqualTo("081804");

        // Timestamp 1234567890s: T = 41152263 -> Expected: 005924
        String code3 = totpService.generateCode(RFC_TEST_SECRET_BASE32, Instant.ofEpochSecond(1234567890L));
        assertThat(code3).isEqualTo("005924");
    }

    @Test
    @DisplayName("verifyCode succeeds within +/- 1 step window and rejects out-of-window codes")
    void verifyCode_driftToleranceWindow() {
        Instant current = Instant.ofEpochSecond(1234567890L); // step = 41152263
        String currentCode = "005924";

        // 1. Current step matches
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, current)).isTrue();

        // 2. Step + 1 to +3 (30s to 90s later) matches currentCode as past-step human typing tolerance
        Instant stepPlus1 = current.plusSeconds(30);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepPlus1)).isTrue();

        Instant stepPlus2 = current.plusSeconds(60);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepPlus2)).isTrue();

        Instant stepPlus3 = current.plusSeconds(90);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepPlus3)).isTrue();

        // 3. Step + 4 (121s later) rejected (expired past maximum tolerance window)
        Instant stepPlus4 = current.plusSeconds(121);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepPlus4)).isFalse();

        // 4. Step - 1 (30s earlier / phone clock ahead) matches
        Instant stepMinus1 = current.minusSeconds(30);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepMinus1)).isTrue();

        // 5. Step - 2 (61s earlier / phone clock too far ahead) rejected
        Instant stepMinus2 = current.minusSeconds(61);
        assertThat(totpService.verifyCode(RFC_TEST_SECRET_BASE32, currentCode, stepMinus2)).isFalse();
    }

    @Test
    @DisplayName("generateBackupCodes creates specified count of formatted recovery codes")
    void generateBackupCodes_createsFormattedDistinctCodes() {
        List<String> codes = totpService.generateBackupCodes(10);

        assertThat(codes).hasSize(10);
        assertThat(codes).doesNotHaveDuplicates();
        for (String code : codes) {
            assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$");
        }
    }
}
