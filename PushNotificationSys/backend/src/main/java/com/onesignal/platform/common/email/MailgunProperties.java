package com.onesignal.platform.common.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Mailgun API Configuration Properties.
 */
@Configuration
@ConfigurationProperties(prefix = "mailgun")
@Data
public class MailgunProperties {

    /**
     * Whether Mailgun email delivery is enabled. If false, emails are simulated/logged.
     */
    private boolean enabled = true;

    /**
     * Mailgun private API Key (e.g., key-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).
     */
    private String apiKey;

    /**
     * Mailgun sending domain (e.g., mg.yourdomain.com or sandbox-xxx.mailgun.org).
     */
    private String domain;

    /**
     * Base Mailgun REST API endpoint (defaults to https://api.mailgun.net/v3).
     */
    private String baseUrl = "https://api.mailgun.net/v3";

    /**
     * Default From header (e.g., "Bronx Notification <postmaster@sandbox...mailgun.org>").
     */
    private String fromAddress;

    /**
     * Webhook signing key for Mailgun incoming webhook verification.
     */
    private String webhookSigningKey;
}
