package com.onesignal.platform.dispatch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class SsrfValidatorTest {

    private SsrfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfValidator();
    }

    @Test
    void testValidateEndpoint_AllowsSimulatedTokens() {
        assertDoesNotThrow(() -> validator.validateEndpoint("mock://device-web-123"));
        assertDoesNotThrow(() -> validator.validateEndpoint("https://fcm.googleapis.com/fcm/send/sample_web_token_789"));
    }

    @Test
    void testValidateEndpoint_RejectsNonHttps() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validator.validateEndpoint("http://fcm.googleapis.com/fcm/send/token123"));
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    @Test
    void testValidateEndpoint_RejectsNonStandardPort() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validator.validateEndpoint("https://fcm.googleapis.com:8443/fcm/send/token123"));
        assertTrue(ex.getMessage().contains("port 443"));
    }

    @Test
    void testValidateEndpoint_RejectsEmptyOrNull() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateEndpoint(""));
        assertThrows(IllegalArgumentException.class, () -> validator.validateEndpoint(null));
    }

    @Test
    void testIsPrivateOrRestricted_LoopbackAndLocal() throws Exception {
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("127.0.0.1")));
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("localhost")));
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("10.0.0.1")));
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("192.168.1.1")));
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("172.16.0.1")));
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("169.254.169.254"))); // AWS metadata
        assertTrue(validator.isPrivateOrRestricted(InetAddress.getByName("100.64.0.1"))); // Carrier-grade NAT
    }

    @Test
    void testIsPrivateOrRestricted_PublicAddress() throws Exception {
        assertFalse(validator.isPrivateOrRestricted(InetAddress.getByName("8.8.8.8")));
        assertFalse(validator.isPrivateOrRestricted(InetAddress.getByName("1.1.1.1")));
    }
}
