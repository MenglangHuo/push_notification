package com.onesignal.platform.common.storage;

import com.onesignal.platform.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalDiskStorageBackend storageBackend;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() throws Exception {
        storageBackend = new LocalDiskStorageBackend(tempDir.toString());
        storageBackend.init();
        fileStorageService = new FileStorageService(storageBackend);
        ReflectionTestUtils.setField(fileStorageService, "maxAvatarDimension", 512);
        ReflectionTestUtils.setField(fileStorageService, "maxAvatarBytes", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(fileStorageService, "jpegQuality", 0.88f);
        ReflectionTestUtils.setField(fileStorageService, "avatarSubDir", "avatars");
    }

    @Test
    @DisplayName("Stores valid oversized JPEG, resizes to <=512px, and returns domain-free relative path")
    void storeAvatar_oversizedJpeg_resizesAndStores() throws Exception {
        byte[] largeJpeg = createImageBytes(1200, 600, BufferedImage.TYPE_INT_RGB, "jpg");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", largeJpeg);
        UUID userId = UUID.randomUUID();

        FileStorageService.StoredAvatar stored = fileStorageService.storeAvatarWithMetadata(file, userId);

        assertThat(stored.relativePath()).startsWith("/avatars/" + userId + "_").endsWith(".jpg");
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(storageBackend.exists(stored.relativePath())).isTrue();

        Path savedFile = tempDir.resolve(stored.relativePath().substring(1));
        BufferedImage savedImg = ImageIO.read(savedFile.toFile());
        assertThat(savedImg.getWidth()).isEqualTo(512);
        assertThat(savedImg.getHeight()).isEqualTo(256);
    }

    @Test
    @DisplayName("Preserves alpha channel as PNG when transparent PNG is uploaded")
    void storeAvatar_transparentPng_preservesPng() throws Exception {
        byte[] pngBytes = createImageBytes(200, 200, BufferedImage.TYPE_INT_ARGB, "png");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes);
        UUID userId = UUID.randomUUID();

        FileStorageService.StoredAvatar stored = fileStorageService.storeAvatarWithMetadata(file, userId);

        assertThat(stored.relativePath()).endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(storageBackend.exists(stored.relativePath())).isTrue();
    }

    @Test
    @DisplayName("Rejects SVG and spoofed non-image files")
    void storeAvatar_rejectsSvgAndSpoofedFiles() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "xss.svg", "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg",
                "not actually an image".getBytes(StandardCharsets.UTF_8)
        );
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> fileStorageService.storeAvatar(svg, userId))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> fileStorageService.storeAvatar(spoofed, userId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Decodes legacy base64 data URI to disk file and deletes cleanly")
    void storeBase64Avatar_andDelete_works() throws Exception {
        byte[] pngBytes = createImageBytes(100, 100, BufferedImage.TYPE_INT_RGB, "png");
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
        UUID userId = UUID.randomUUID();

        String path = fileStorageService.storeBase64Avatar(dataUri, userId);

        assertThat(path).startsWith("/avatars/" + userId + "_");
        assertThat(Files.exists(tempDir.resolve(path.substring(1)))).isTrue();

        assertThat(fileStorageService.deleteFile(path)).isTrue();
        assertThat(Files.exists(tempDir.resolve(path.substring(1)))).isFalse();
    }

    private byte[] createImageBytes(int w, int h, int imageType, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, imageType);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }
}
