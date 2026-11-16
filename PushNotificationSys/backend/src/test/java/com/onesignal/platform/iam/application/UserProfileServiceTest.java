package com.onesignal.platform.iam.application;

import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.storage.AvatarUrlResolver;
import com.onesignal.platform.common.storage.FileStorageService;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    private AvatarUrlResolver avatarUrlResolver;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        avatarUrlResolver = new AvatarUrlResolver("https://api.example.com", "/uploads", "");
        userProfileService = new UserProfileService(userRepository, avatarUrlResolver, fileStorageService);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Stores domain-free relative path when given resolved own-domain URL without deleting file")
    void updateProfile_echoedOwnUrl_normalizesAndKeepsFile() {
        User user = User.builder()
                .email("a@b.com")
                .passwordHash("h")
                .fullName("Old Name")
                .avatarUrl("/avatars/u1_100.jpg")
                .build();
        user.setId(UUID.randomUUID());

        User updated = userProfileService.updateProfile(
                user,
                "New Name",
                "https://api.example.com/uploads/avatars/u1_100.jpg",
                false
        );

        assertThat(updated.getFullName()).isEqualTo("New Name");
        assertThat(updated.getAvatarUrl()).isEqualTo("/avatars/u1_100.jpg");
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("Deletes superseded local avatar file when replaced by new upload or external URL")
    void updateProfile_replacedAvatar_deletesOldLocalFile() {
        User user = User.builder()
                .email("a@b.com")
                .passwordHash("h")
                .fullName("Alice")
                .avatarUrl("/avatars/u1_old.jpg")
                .build();
        user.setId(UUID.randomUUID());

        User updated = userProfileService.updateProfile(
                user,
                null,
                "https://avatars.githubusercontent.com/u/99",
                false
        );

        assertThat(updated.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/99");
        verify(fileStorageService).deleteFile("/avatars/u1_old.jpg");
    }

    @Test
    @DisplayName("Clears avatar and deletes local file when removeAvatar=true or blank string")
    void updateProfile_removeAvatar_clearsAndDeletesFile() {
        User user = User.builder()
                .email("a@b.com")
                .passwordHash("h")
                .fullName("Alice")
                .avatarUrl("/avatars/u1_old.jpg")
                .build();
        user.setId(UUID.randomUUID());

        User updated = userProfileService.updateProfile(user, null, "", true);

        assertThat(updated.getAvatarUrl()).isNull();
        verify(fileStorageService).deleteFile("/avatars/u1_old.jpg");
    }

    @Test
    @DisplayName("Rejects base64 data URI submissions")
    void updateProfile_base64_throwsBadRequest() {
        User user = User.builder().email("a@b.com").passwordHash("h").fullName("Alice").build();

        assertThatThrownBy(() ->
                userProfileService.updateProfile(user, "Alice", "data:image/png;base64,AAAA", false))
                .isInstanceOf(AppException.class);
        verify(userRepository, never()).save(any());
    }
}
