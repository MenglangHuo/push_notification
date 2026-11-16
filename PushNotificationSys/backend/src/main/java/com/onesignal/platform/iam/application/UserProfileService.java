package com.onesignal.platform.iam.application;

import com.onesignal.platform.common.storage.AvatarUrlResolver;
import com.onesignal.platform.common.storage.FileStorageService;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared profile update logic for {@link User} entities across both
 * self-service (/auth/me/profile) and organization member (/members/{id}) endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final FileStorageService fileStorageService;

    @Transactional
    public User updateProfile(User user, String fullName, String rawAvatarUrl, Boolean removeAvatar) {
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        boolean shouldClearAvatar = Boolean.TRUE.equals(removeAvatar)
                || (rawAvatarUrl != null && rawAvatarUrl.isBlank());

        if (shouldClearAvatar) {
            deleteOldLocalAvatar(user.getAvatarUrl(), user.getId());
            user.setAvatarUrl(null);
        } else if (rawAvatarUrl != null) {
            String normalizedNew = avatarUrlResolver.normalizeForStorage(rawAvatarUrl);
            String oldAvatar = user.getAvatarUrl();

            if (oldAvatar != null && !oldAvatar.equals(normalizedNew)) {
                deleteOldLocalAvatar(oldAvatar, user.getId());
            }
            user.setAvatarUrl(normalizedNew);
        }

        return userRepository.save(user);
    }

    private void deleteOldLocalAvatar(String oldAvatar, java.util.UUID userId) {
        if (oldAvatar != null && avatarUrlResolver.isLocalPath(oldAvatar)) {
            fileStorageService.deleteFile(oldAvatar);
            log.debug("Cleaned up superseded local avatar for user {}: {}", userId, oldAvatar);
        }
    }
}
