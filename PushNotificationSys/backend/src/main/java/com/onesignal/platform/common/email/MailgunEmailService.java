package com.onesignal.platform.common.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready Mailgun REST API v3 Email Delivery Service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailgunEmailService implements EmailService {

    private final MailgunProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean sendEmail(String to, String subject, String htmlBody, String textBody) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.info("[Mailgun Disabled/Simulated] To: {} | Subject: '{}'", to, subject);
            return true;
        }

        try {
            String endpoint = String.format("%s/%s/messages",
                    properties.getBaseUrl().replaceAll("/+$", ""),
                    properties.getDomain());

            Map<String, String> formData = new LinkedHashMap<>();
            formData.put("from", properties.getFromAddress());
            formData.put("to", to);
            formData.put("subject", subject);
            if (htmlBody != null && !htmlBody.isBlank()) {
                formData.put("html", htmlBody);
            }
            if (textBody != null && !textBody.isBlank()) {
                formData.put("text", textBody);
            }

            // Disable Mailgun click tracking so direct localhost/frontend links are not rewritten to unresolvable sandbox CNAMEs
            formData.put("o:tracking", "no");
            formData.put("o:tracking-clicks", "no");

            String requestBody = buildFormData(formData);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    ("api:" + properties.getApiKey()).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[Mailgun Success] Sent email to {} | Subject: '{}' | Status: {} | Body: {}",
                        to, subject, response.statusCode(), response.body());
                return true;
            } else {
                log.error("[Mailgun Failed] Could not send email to {} | Status: {} | Response: {}",
                        to, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[Mailgun Error] Exception while sending email to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Async
    public void sendInvitationEmail(String toEmail, String orgName, String roleName, String appName, String inviteUrl) {
        String appScopeText = (appName != null && !appName.isBlank()) ? appName : "Entire Organization (All Apps)";
        String subject = String.format("You've been invited to join %s on Bronx Notification", orgName);

        String html = buildInvitationHtml(orgName, roleName, appScopeText, inviteUrl);
        String text = buildInvitationText(orgName, roleName, appScopeText, inviteUrl);

        sendEmail(toEmail, subject, html, text);
    }

    private String buildFormData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue() != null ? entry.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String buildInvitationHtml(String orgName, String roleName, String appScopeText, String inviteUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Team Invitation</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #0f172a; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e2e8f0;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color: #0f172a; padding: 30px 10px;">
                        <tr>
                            <td align="center">
                                <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 580px; background-color: #1e293b; border: 1px solid #334155; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);">
                                    <!-- Header -->
                                    <tr>
                                        <td align="center" style="background-color: #4f46e5; padding: 36px 30px; text-align: center;">
                                            <div style="display: inline-block; padding: 6px 14px; background-color: rgba(255, 255, 255, 0.18); border-radius: 9999px; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; color: #ffffff; text-transform: uppercase; margin-bottom: 12px;">
                                                Bronx Push Notification Engine
                                            </div>
                                            <h1 style="margin: 0; font-size: 24px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px; line-height: 1.3;">
                                                You're Invited to Join %s
                                            </h1>
                                        </td>
                                    </tr>

                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 36px 32px 28px 32px; text-align: left;">
                                            <p style="font-size: 15px; line-height: 1.6; color: #cbd5e1; margin: 0 0 16px 0;">Hello,</p>
                                            <p style="font-size: 15px; line-height: 1.6; color: #cbd5e1; margin: 0 0 24px 0;">
                                                You have been invited to collaborate with your team on the <strong style="color: #ffffff;">%s</strong> workspace. Review your access details below:
                                            </p>

                                            <!-- Metadata Card -->
                                            <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color: #0f172a; border: 1px solid #334155; border-radius: 14px; margin-bottom: 28px;">
                                                <tr>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #94a3b8; font-size: 14px; font-weight: 500;">Organization</td>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #f8fafc; font-size: 14px; font-weight: 600; text-align: right;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #94a3b8; font-size: 14px; font-weight: 500;">Assigned Role</td>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #f8fafc; font-size: 14px; font-weight: 600; text-align: right;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #94a3b8; font-size: 14px; font-weight: 500;">Application Scope</td>
                                                    <td style="padding: 14px 20px; border-bottom: 1px solid #1e293b; color: #f8fafc; font-size: 14px; font-weight: 600; text-align: right;">%s</td>
                                                </tr>
                                                <tr>
                                                    <td style="padding: 14px 20px; color: #94a3b8; font-size: 14px; font-weight: 500;">Expiration</td>
                                                    <td style="padding: 14px 20px; color: #f8fafc; font-size: 14px; font-weight: 600; text-align: right;">7 Days</td>
                                                </tr>
                                            </table>

                                            <!-- Bulletproof CTA Button -->
                                            <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="margin: 32px 0 24px 0;">
                                                <tr>
                                                    <td align="center">
                                                        <table border="0" cellpadding="0" cellspacing="0" style="border-collapse: separate; margin: 0 auto;">
                                                            <tr>
                                                                <td align="center" bgcolor="#4f46e5" style="border-radius: 12px; background-color: #4f46e5;">
                                                                    <a href="%s" target="_blank" style="display: inline-block; padding: 16px 36px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 16px; font-weight: 700; color: #ffffff !important; text-decoration: none; border-radius: 12px; background-color: #4f46e5; border: 1px solid #4f46e5; cursor: pointer; text-align: center;">
                                                                        Accept Invitation &amp; Set Up Account &rarr;
                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Fallback Direct URL -->
                                            <div style="margin-top: 24px; padding: 16px; background-color: #0f172a; border: 1px dashed #334155; border-radius: 12px; font-size: 12px; color: #94a3b8; word-break: break-all; line-height: 1.6;">
                                                <span style="color: #cbd5e1; font-weight: 600;">Button not clickable?</span> Copy and paste this URL directly into your browser:<br/>
                                                <a href="%s" target="_blank" style="color: #818cf8; text-decoration: underline; font-weight: 500;">%s</a>
                                            </div>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td align="center" style="padding: 24px 32px; background-color: #0f172a; border-top: 1px solid #334155; text-align: center; font-size: 12px; color: #64748b; line-height: 1.5;">
                                            &copy; 2026 Bronx Push Notification Platform. All rights reserved.<br/>
                                            If you did not expect this invitation, you can safely ignore this email.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(orgName),
                escapeHtml(orgName),
                escapeHtml(orgName),
                escapeHtml(roleName),
                escapeHtml(appScopeText),
                inviteUrl,
                inviteUrl,
                inviteUrl
        );
    }

    private String buildInvitationText(String orgName, String roleName, String appScopeText, String inviteUrl) {
        return """
                You're invited to join %s on Bronx Push Notification Platform!
                
                Organization: %s
                Role: %s
                Application Scope: %s
                Expiration: 7 days
                
                Click the link below to accept the invitation and set up your account:
                %s
                
                If you did not expect this invitation, you can safely ignore this email.
                """.formatted(orgName, orgName, roleName, appScopeText, inviteUrl);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
