package com.onesignal.platform.common.api;

import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.common.storage.AvatarUrlResolver;
import com.onesignal.platform.common.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    private AvatarUrlResolver avatarUrlResolver;
    private FileUploadController controller;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        avatarUrlResolver = new AvatarUrlResolver("https://api.example.com", "/uploads", "");
        controller = new FileUploadController(fileStorageService, avatarUrlResolver);
        TenantContextHolder.setContext(TenantContextHolder.TenantContext.builder()
                .userId(userId)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("uploadAvatar returns relative path and domain-resolved url")
    void uploadAvatar_returnsPathAndResolvedUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String relPath = "/avatars/" + userId + "_12345_abcd1234.jpg";
        when(fileStorageService.storeAvatarWithMetadata(file, userId))
                .thenReturn(new FileStorageService.StoredAvatar(relPath, "image/jpeg", 1024L));

        ResponseEntity<Map<String, Object>> response = controller.uploadAvatar(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("path", relPath)
                .containsEntry("url", "https://api.example.com/uploads" + relPath)
                .containsEntry("contentType", "image/jpeg")
                .containsEntry("sizeBytes", 1024L);
    }

    @Test
    @DisplayName("deleteAvatar enforces strict user ownership prefix")
    void deleteAvatar_otherUsersFile_throwsForbidden() {
        UUID otherUser = UUID.randomUUID();
        String otherPath = "/avatars/" + otherUser + "_12345_abcd1234.jpg";

        assertThatThrownBy(() -> controller.deleteAvatar(otherPath))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("another user");
    }

    @Test
    @DisplayName("deleteAvatar deletes owned avatar even when full resolved URL is passed")
    void deleteAvatar_ownedFullUrl_normalizesAndDeletes() {
        String relPath = "/avatars/" + userId + "_12345_abcd1234.jpg";
        String fullUrl = "https://api.example.com/uploads" + relPath;

        ResponseEntity<Void> response = controller.deleteAvatar(fullUrl);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(fileStorageService).deleteFile(relPath);
    }
}
