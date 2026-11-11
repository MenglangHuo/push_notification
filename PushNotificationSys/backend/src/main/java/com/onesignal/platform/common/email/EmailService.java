package com.onesignal.platform.common.email;

/**
 * Contract for transactional email dispatch.
 */
public interface EmailService {

    /**
     * Dispatch an email with HTML and fallback plain text bodies.
     *
     * @param to       recipient email address
     * @param subject  email subject line
     * @param htmlBody rich HTML content
     * @param textBody plain text fallback content
     * @return true if successfully queued/sent by provider, false otherwise
     */
    boolean sendEmail(String to, String subject, String htmlBody, String textBody);

    /**
     * Dispatch an organization member invitation email with one-click setup link.
     *
     * @param toEmail   recipient email address
     * @param orgName   inviting organization name
     * @param roleName  assigned role name
     * @param appName   assigned application scope (or null for org-wide)
     * @param inviteUrl complete onboarding URL with cryptographic token
     * @return true if successfully dispatched, false otherwise
     */
    void sendInvitationEmail(String toEmail, String orgName, String roleName, String appName, String inviteUrl);
}
