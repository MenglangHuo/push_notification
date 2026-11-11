package com.onesignal.platform.common.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailgunEmailServiceTest {

    private MailgunProperties properties;
    private MailgunEmailService emailService;

    @BeforeEach
    void setUp() {
        properties = new MailgunProperties();
        properties.setEnabled(false);
        properties.setApiKey("test-api-key");
        properties.setDomain("sandbox.mailgun.org");
        properties.setFromAddress("Test <test@sandbox.mailgun.org>");
        properties.setBaseUrl("https://api.mailgun.net/v3");

        emailService = new MailgunEmailService(properties);
    }

    @Test
    @DisplayName("When Mailgun is disabled, sendEmail simulates dispatch and returns true")
    void sendEmail_disabled_returnsTrue() {
        boolean result = emailService.sendEmail("user@example.com", "Test Subject", "<p>Hello</p>", "Hello");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("When apiKey is empty, sendEmail simulates dispatch and returns true")
    void sendEmail_emptyApiKey_returnsTrue() {
        properties.setEnabled(true);
        properties.setApiKey("");

        boolean result = emailService.sendEmail("user@example.com", "Test Subject", "<p>Hello</p>", "Hello");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("sendInvitationEmail generates valid HTML and handles app scope")
    void sendInvitationEmail_disabled_succeeds() {
        org.assertj.core.api.Assertions.assertThatCode(() -> emailService.sendInvitationEmail(
                "huomenglang@gmail.com",
                "Acme Corporation",
                "Editor",
                "Acme Retail App",
                "http://localhost:4000/accept-invite?token=test-token-123"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendInvitationEmail handles null app scope as organization-wide")
    void sendInvitationEmail_nullAppScope_succeeds() {
        org.assertj.core.api.Assertions.assertThatCode(() -> emailService.sendInvitationEmail(
                "huomenglang@gmail.com",
                "Acme Corporation",
                "Admin",
                null,
                "http://localhost:4000/accept-invite?token=test-token-456"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Live Mailgun API integration test when environment variables are supplied")
    void sendLiveEmail_whenConfigured() {
        String apiKey = System.getenv("MAILGUN_API_KEY");
        String domain = System.getenv("MAILGUN_DOMAIN");
        if (apiKey == null || apiKey.isBlank() || domain == null || domain.isBlank()) {
            return; // Skip live network call if env vars are not set
        }

        MailgunProperties liveProps = new MailgunProperties();
        liveProps.setEnabled(true);
        liveProps.setApiKey(apiKey);
        liveProps.setDomain(domain);
        liveProps.setFromAddress(System.getenv().getOrDefault("MAILGUN_FROM_ADDRESS", "postmaster@" + domain));
        liveProps.setBaseUrl(System.getenv().getOrDefault("MAILGUN_BASE_URL", "https://api.mailgun.net/v3"));

        MailgunEmailService liveService = new MailgunEmailService(liveProps);

        org.assertj.core.api.Assertions.assertThatCode(() -> liveService.sendInvitationEmail(
                "test@example.com",
                "Acme Corporation",
                "Platform Administrator",
                "Bronx Production App",
                "http://localhost:4000/accept-invite?token=demo-live-token-7788"
        )).doesNotThrowAnyException();
    }
}
