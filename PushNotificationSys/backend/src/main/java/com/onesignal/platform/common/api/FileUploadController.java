package com.onesignal.platform.common.api;

import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.common.storage.AvatarUrlResolver;
import com.onesignal.platform.common.storage.FileStorageService;
import com.onesignal.platform.common.storage.FileStorageService.StoredAvatar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Handles file upload and deletion operations for user assets.
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final AvatarUrlResolver avatarUrlResolver;

    /**
     * Upload an avatar image.
     * <p>
     * Validates binary format & dimensions, strips EXIF metadata, resizes to max 512x512,
     * and persists via the configured {@code StorageBackend}.
     *
     * @param file the uploaded image file
     * @return JSON containing domain-free {@code path} (for DB persistence), resolved {@code url} (for display),
     *         {@code contentType}, and {@code sizeBytes}
     */
    @PostMapping(value = "/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {

        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }

        if (file.isEmpty()) {
            throw new AppException("File is empty", HttpStatus.BAD_REQUEST);
        }

        try {
            StoredAvatar stored = fileStorageService.storeAvatarWithMetadata(file, userId);
            String fullUrl = avatarUrlResolver.resolve(stored.relativePath());

            log.info("Avatar uploaded for user {}: {}", userId, stored.relativePath());

            return ResponseEntity.ok(Map.of(
                    "path", stored.relativePath(),
                    "url", fullUrl,
                    "contentType", stored.contentType(),
                    "sizeBytes", stored.sizeBytes()
            ));
        } catch (IOException e) {
            log.error("Failed to store avatar for user {}: {}", userId, e.getMessage());
            throw new AppException("Failed to store avatar image", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete a previously uploaded avatar file belonging to the authenticated user.
     * Accepts either a relative path (e.g. "/avatars/...") or a resolved URL.
     */
    @DeleteMapping("/avatars")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAvatar(@RequestParam("path") String pathOrUrl) {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }

        String normalizedPath = avatarUrlResolver.normalizeForStorage(pathOrUrl);
        if (normalizedPath == null || !avatarUrlResolver.isLocalPath(normalizedPath)) {
            throw new AppException("Only managed local avatar files can be deleted", HttpStatus.BAD_REQUEST);
        }

        // Strict ownership check: filename must begin with /avatars/{userId}_
        String expectedPrefix = "/avatars/" + userId + "_";
        if (!normalizedPath.startsWith(expectedPrefix)) {
            throw new AppException("Cannot delete another user's avatar", HttpStatus.FORBIDDEN);
        }

        fileStorageService.deleteFile(normalizedPath);
        log.info("Avatar deleted for user {}: {}", userId, normalizedPath);

        return ResponseEntity.noContent().build();
    }
}
