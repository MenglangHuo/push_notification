package com.onesignal.platform.common.storage;

import com.onesignal.platform.common.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Resolves and normalizes user avatar URLs between database persistence and API responses.
 * <p>
 * Storage rule (DB {@code users.avatar_url}):
 * <ul>
 *   <li>Uploaded files -> relative path only (e.g. {@code "/avatars/uuid_ts_nonce.jpg"}) — domain-independent</li>
 *   <li>External image URLs -> full external URL as-is (e.g. {@code "https://avatars.githubusercontent.com/u/123"})</li>
 *   <li>No avatar -> {@code null}</li>
 * </ul>
 * <p>
 * Response rule (API DTOs):
 * <ul>
 *   <li>Relative path -> prepends {@code app.upload.cdn-base-url} (if set) or {@code app.base-url + /uploads}</li>
 *   <li>External URL -> returned unchanged</li>
 *   <li>{@code null} -> {@code null}</li>
 * </ul>
 */
@Component
public class AvatarUrlResolver {

    private final String baseUrl;
    private final String publicPathPrefix;
    private final String cdnBaseUrl;

    public AvatarUrlResolver(
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.upload.public-path-prefix:/uploads}") String publicPathPrefix,
            @Value("${app.upload.cdn-base-url:}") String cdnBaseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        String prefix = publicPathPrefix == null || publicPathPrefix.isBlank() ? "/uploads" : publicPathPrefix.trim();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        this.publicPathPrefix = trimTrailingSlash(prefix);
        this.cdnBaseUrl = cdnBaseUrl == null ? "" : trimTrailingSlash(cdnBaseUrl.trim());
    }

    /**
     * Resolves a stored database value into a browser-accessible URL for API responses.
     */
    public String resolve(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }

        String trimmed = storedValue.trim();

        // External URLs: pass through unchanged
        if (isExternal(trimmed)) {
            return trimmed;
        }

        // Legacy unmigrated base64 data URIs: pass through until AvatarMigrationTask runs
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }

        // Relative path: prepend CDN base URL or backend baseUrl + publicPathPrefix
        String path = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        if (!cdnBaseUrl.isBlank()) {
            return cdnBaseUrl + path;
        }
        if (path.startsWith(publicPathPrefix + "/")) {
            return baseUrl + path;
        }
        return baseUrl + publicPathPrefix + path;
    }

    /**
     * Normalizes and validates an incoming avatar value before persisting to {@code users.avatar_url}.
     * Ensures uploaded local URLs are stripped down to domain-free relative paths,
     * external URLs are validated and stored as-is, and base64 data URIs are rejected.
     */
    public String normalizeForStorage(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();

        if (trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) {
            throw new AppException(
                    "Base64 data URIs are no longer accepted for avatarUrl. Upload files via POST /admin/api/v1/files/avatars or supply an http(s) URL.",
                    HttpStatus.BAD_REQUEST);
        }

        // Strip configured CDN base URL if caller echoed back a resolved URL
        if (!cdnBaseUrl.isBlank() && startsWithIgnoreCaseOrigin(trimmed, cdnBaseUrl + "/")) {
            String stripped = trimmed.substring(cdnBaseUrl.length());
            return validateRelativePath(stripped);
        }

        // Strip configured backend baseUrl + /uploads prefix if caller echoed back a resolved URL
        String selfUploadPrefix = baseUrl + publicPathPrefix;
        if (startsWithIgnoreCaseOrigin(trimmed, selfUploadPrefix + "/")) {
            String stripped = trimmed.substring(selfUploadPrefix.length());
            return validateRelativePath(stripped);
        }

        // Strip /uploads prefix if caller passed /uploads/avatars/...
        if (trimmed.startsWith(publicPathPrefix + "/")) {
            return validateRelativePath(trimmed.substring(publicPathPrefix.length()));
        }

        // External http(s) URL
        if (isExternal(trimmed)) {
            validateExternalUrl(trimmed);
            return trimmed;
        }

        // Relative local path
        String candidate = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return validateRelativePath(candidate);
    }

    /**
     * Checks whether a URL is an external (absolute http/https) URL.
     */
    public boolean isExternal(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * Checks whether a stored value represents a managed local/storage file path.
     */
    public boolean isLocalPath(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return false;
        String trimmed = storedValue.trim();
        return !isExternal(trimmed) && !trimmed.startsWith("data:");
    }

    private String validateRelativePath(String path) {
        if (path.contains("..") || path.contains("\\")) {
            throw new AppException("Invalid avatar file path", HttpStatus.BAD_REQUEST);
        }
        if (!path.startsWith("/avatars/") || path.length() <= "/avatars/".length()) {
            throw new AppException(
                    "Invalid avatar path. Local paths must be under /avatars/ or use a valid http(s) URL.",
                    HttpStatus.BAD_REQUEST);
        }
        return path;
    }

    private void validateExternalUrl(String url) {
        if (url.length() > 2048) {
            throw new AppException("Avatar URL exceeds maximum length of 2048 characters", HttpStatus.BAD_REQUEST);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new AppException("Invalid external avatar URL", HttpStatus.BAD_REQUEST);
            }
        } catch (IllegalArgumentException e) {
            throw new AppException("Malformed external avatar URL", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean startsWithIgnoreCaseOrigin(String fullUrl, String prefixWithSlash) {
        if (fullUrl.length() < prefixWithSlash.length()) {
            return false;
        }
        return fullUrl.substring(0, prefixWithSlash.length()).equalsIgnoreCase(prefixWithSlash);
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
