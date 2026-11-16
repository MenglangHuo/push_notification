package com.onesignal.platform.common.storage;

import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-time migration task that converts existing base64 data-URI avatars
 * to files on disk. Activate by setting:
 * <pre>
 *   app.migration.avatar-base64=true
 * </pre>
 * <p>
 * Safe to run multiple times (idempotent): only processes rows
 * where avatar_url starts with "data:image".
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.migration.avatar-base64", havingValue = "true")
public class AvatarMigrationTask {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateBase64Avatars() {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║  AVATAR MIGRATION: base64 → file storage        ║");
        log.info("╚══════════════════════════════════════════════════╝");

        List<User> usersWithBase64 = userRepository.findAll().stream()
                .filter(u -> u.getAvatarUrl() != null && u.getAvatarUrl().startsWith("data:image"))
                .toList();

        if (usersWithBase64.isEmpty()) {
            log.info("No base64 avatars found. Migration complete (nothing to do).");
            return;
        }

        log.info("Found {} users with base64 avatars to migrate.", usersWithBase64.size());

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (User user : usersWithBase64) {
            try {
                String relativePath = fileStorageService.storeBase64Avatar(
                        user.getAvatarUrl(), user.getId());

                if (relativePath != null) {
                    user.setAvatarUrl(relativePath);
                    userRepository.save(user);
                    success.incrementAndGet();
                    log.info("  ✓ Migrated user {} → {}", user.getId(), relativePath);
                } else {
                    failed.incrementAndGet();
                    log.warn("  ✗ Skipped user {} (decode failed)", user.getId());
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("  ✗ Failed user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("════════════════════════════════════════════════════");
        log.info("  Migration complete: {} succeeded, {} failed, {} total",
                success.get(), failed.get(), usersWithBase64.size());
        log.info("════════════════════════════════════════════════════");
    }
}
