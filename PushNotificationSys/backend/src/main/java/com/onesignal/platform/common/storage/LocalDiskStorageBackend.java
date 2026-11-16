package com.onesignal.platform.common.storage;

import com.onesignal.platform.common.exception.AppException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem / AWS EC2 EBS volume implementation of {@link StorageBackend}.
 * Writes atomically via temporary files and defends against path traversal.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageBackend implements StorageBackend {

    private final Path uploadDir;

    public LocalDiskStorageBackend(@Value("${app.upload.dir:./uploads}") String uploadDirStr) {
        this.uploadDir = Paths.get(uploadDirStr).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir.resolve("avatars"));
        log.info("LocalDiskStorageBackend initialized at: {}", uploadDir);
    }

    @Override
    public void store(String relativePath, byte[] content, String contentType) throws IOException {
        Path targetPath = resolveSafePath(relativePath);
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Write to temp file in same directory, then atomically move to prevent partial reads
        Path tempFile = Files.createTempFile(parent, ".upload_", ".tmp");
        try {
            Files.write(tempFile, content);
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public boolean delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            Path targetPath = resolveSafePath(relativePath);
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("Deleted local file: {}", relativePath);
            }
            return deleted;
        } catch (AppException e) {
            log.warn("Rejected unsafe file delete path: {}", relativePath);
            return false;
        } catch (IOException e) {
            log.warn("Failed to delete local file {}: {}", relativePath, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            return Files.exists(resolveSafePath(relativePath));
        } catch (AppException e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "local";
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    private Path resolveSafePath(String relativePath) {
        String clean = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        Path resolved = uploadDir.resolve(clean).normalize();
        if (!resolved.startsWith(uploadDir) || resolved.equals(uploadDir)) {
            throw new AppException("Invalid file path", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }
}
