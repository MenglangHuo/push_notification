package com.onesignal.platform.common.storage;

import java.io.IOException;

/**
 * Storage provider abstraction for user-uploaded assets.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link LocalDiskStorageBackend} — local disk / AWS EC2 EBS volume (default)</li>
 *   <li>Future: S3StorageBackend — Amazon S3 / MinIO object storage</li>
 * </ul>
 */
public interface StorageBackend {

    /**
     * Persists bytes at the given relative path (e.g. "/avatars/uuid_ts.jpg").
     */
    void store(String relativePath, byte[] content, String contentType) throws IOException;

    /**
     * Deletes the file at the given relative path. Returns true if a file was deleted.
     */
    boolean delete(String relativePath);

    /**
     * Checks whether a file exists at the given relative path.
     */
    boolean exists(String relativePath);

    /**
     * Identifier for the active storage provider (e.g. "local", "s3").
     */
    String getProviderName();
}
