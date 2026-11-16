package com.onesignal.platform.common.storage;

import com.onesignal.platform.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Handles image validation, sanitization, resizing, and storage for user-uploaded assets.
 * Delegates physical persistence to {@link StorageBackend} (local disk / EC2 EBS or future S3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final int MAX_RAW_DIMENSION = 8192;
    private static final long MAX_RAW_PIXELS = 25_000_000L; // ~25MP decompression-bomb guard
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_IMAGE_FORMATS = Set.of(
            "jpeg", "jpg", "png", "webp", "gif"
    );

    private final StorageBackend storageBackend;

    @Value("${app.upload.avatar.max-dimension:512}")
    private int maxAvatarDimension = 512;

    @Value("${app.upload.avatar.max-bytes:5242880}")
    private long maxAvatarBytes = 5L * 1024 * 1024;

    @Value("${app.upload.avatar.jpeg-quality:0.88}")
    private float jpegQuality = 0.88f;

    @Value("${app.upload.avatar.sub-dir:avatars}")
    private String avatarSubDir = "avatars";

    public record StoredAvatar(String relativePath, String contentType, long sizeBytes) {}

    /**
     * Validates, sanitizes (strips EXIF), resizes, and stores an uploaded avatar image.
     */
    public String storeAvatar(MultipartFile file, UUID userId) throws IOException {
        return storeAvatarWithMetadata(file, userId).relativePath();
    }

    /**
     * Same as {@link #storeAvatar(MultipartFile, UUID)} but also returns output contentType and byte size.
     */
    public StoredAvatar storeAvatarWithMetadata(MultipartFile file, UUID userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException("File is empty", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > maxAvatarBytes) {
            throw new AppException(
                    "File too large. Maximum allowed size is " + (maxAvatarBytes / (1024 * 1024)) + " MB",
                    HttpStatus.BAD_REQUEST);
        }

        String declaredType = file.getContentType();
        if (declaredType != null && !declaredType.isBlank()
                && !declaredType.equalsIgnoreCase("application/octet-stream")
                && !ALLOWED_CONTENT_TYPES.contains(declaredType.toLowerCase(Locale.ROOT))) {
            throw new AppException(
                    "Invalid file type. Allowed: JPEG, PNG, WebP, GIF", HttpStatus.BAD_REQUEST);
        }

        byte[] rawBytes = file.getBytes();
        return processAndStoreImage(rawBytes, userId);
    }

    /**
     * Decodes a legacy base64 data URI and stores it as a normalized image file.
     * Used by {@link AvatarMigrationTask}.
     */
    public String storeBase64Avatar(String dataUri, UUID userId) {
        try {
            if (dataUri == null || !dataUri.startsWith("data:image")) {
                log.warn("Invalid data URI format for user {}", userId);
                return null;
            }

            String[] parts = dataUri.split(",", 2);
            if (parts.length != 2 || !parts[0].contains(";base64")) {
                log.warn("Malformed base64 data URI for user {}", userId);
                return null;
            }

            byte[] imageBytes = Base64.getDecoder().decode(parts[1].trim());
            StoredAvatar stored = processAndStoreImage(imageBytes, userId);
            log.info("Migrated base64 avatar for user {} -> {} ({} bytes)",
                    userId, stored.relativePath(), stored.sizeBytes());
            return stored.relativePath();
        } catch (Exception e) {
            log.error("Failed to migrate base64 avatar for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a previously stored file by its relative path.
     */
    public boolean deleteFile(String relativePath) {
        return storageBackend.delete(relativePath);
    }

    // ─── Internal Image Pipeline ──────────────────────────────────────────

    private StoredAvatar processAndStoreImage(byte[] rawBytes, UUID userId) throws IOException {
        BufferedImage decoded = readAndValidateImage(new ByteArrayInputStream(rawBytes));

        boolean preserveAlpha = decoded.getColorModel().hasAlpha();
        BufferedImage sanitized = redrawAndResize(decoded, maxAvatarDimension, preserveAlpha);

        String extension = preserveAlpha ? "png" : "jpg";
        String outputContentType = preserveAlpha ? "image/png" : "image/jpeg";
        byte[] encodedBytes = encodeImage(sanitized, preserveAlpha);

        String shortNonce = UUID.randomUUID().toString().substring(0, 8);
        String filename = userId + "_" + System.currentTimeMillis() + "_" + shortNonce + "." + extension;
        String cleanSubDir = avatarSubDir.replaceAll("^/+|/+$", "");
        String relativePath = "/" + cleanSubDir + "/" + filename;

        storageBackend.store(relativePath, encodedBytes, outputContentType);
        log.info("Avatar stored via [{}]: {} (input={}B -> output={}B, type={})",
                storageBackend.getProviderName(), relativePath, rawBytes.length, encodedBytes.length, outputContentType);

        return new StoredAvatar(relativePath, outputContentType, encodedBytes.length);
    }

    private BufferedImage readAndValidateImage(InputStream in) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new AppException("Invalid or corrupted image file", HttpStatus.BAD_REQUEST);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new AppException("Unsupported or invalid image format. Allowed: JPEG, PNG, WebP, GIF",
                        HttpStatus.BAD_REQUEST);
            }

            ImageReader reader = readers.next();
            try {
                String detectedFormat = reader.getFormatName() != null
                        ? reader.getFormatName().toLowerCase(Locale.ROOT)
                        : "";
                if (!ALLOWED_IMAGE_FORMATS.contains(detectedFormat)) {
                    throw new AppException("Unsupported image format: " + detectedFormat, HttpStatus.BAD_REQUEST);
                }

                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new AppException("Invalid image dimensions", HttpStatus.BAD_REQUEST);
                }
                if (width > MAX_RAW_DIMENSION || height > MAX_RAW_DIMENSION
                        || (long) width * height > MAX_RAW_PIXELS) {
                    throw new AppException(
                            "Image dimensions exceed maximum allowed limits (" + MAX_RAW_DIMENSION + "px)",
                            HttpStatus.BAD_REQUEST);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new AppException("Could not decode image contents", HttpStatus.BAD_REQUEST);
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Always redraws onto a new BufferedImage to strip EXIF/metadata and enforce max dimension.
     */
    private BufferedImage redrawAndResize(BufferedImage original, int maxDim, boolean preserveAlpha) {
        int width = original.getWidth();
        int height = original.getHeight();

        int targetWidth = width;
        int targetHeight = height;
        if (width > maxDim || height > maxDim) {
            double scale = Math.min((double) maxDim / width, (double) maxDim / height);
            targetWidth = Math.max(1, (int) Math.round(width * scale));
            targetHeight = Math.max(1, (int) Math.round(height * scale));
        }

        int imageType = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g2d = output.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!preserveAlpha) {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, targetWidth, targetHeight);
            }
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return output;
    }

    private byte[] encodeImage(BufferedImage image, boolean asPng) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (asPng) {
            if (!ImageIO.write(image, "png", baos)) {
                throw new IOException("PNG writer unavailable");
            }
            return baos.toByteArray();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer unavailable");
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(mcios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.5f, Math.min(1.0f, jpegQuality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
