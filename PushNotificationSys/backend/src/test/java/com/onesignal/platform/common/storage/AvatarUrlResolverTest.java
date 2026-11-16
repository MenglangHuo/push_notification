package com.onesignal.platform.common.storage;

import com.onesignal.platform.common.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarUrlResolverTest {

    private final AvatarUrlResolver resolver = new AvatarUrlResolver(
            "https://api.example.com/",
            "/uploads",
            ""
    );

    @Test
    @DisplayName("Resolves relative path by prepending configured domain + /uploads")
    void resolve_relativePath_prependsDomain() {
        assertThat(resolver.resolve("/avatars/user_123.jpg"))
                .isEqualTo("https://api.example.com/uploads/avatars/user_123.jpg");
        assertThat(resolver.resolve("avatars/user_123.jpg"))
                .isEqualTo("https://api.example.com/uploads/avatars/user_123.jpg");
    }

    @Test
    @DisplayName("Passes external http(s) URLs through without prepending local domain")
    void resolve_externalUrl_unchanged() {
        String googleAvatar = "https://lh3.googleusercontent.com/a/photo.jpg";
        assertThat(resolver.resolve(googleAvatar)).isEqualTo(googleAvatar);

        String httpAvatar = "http://cdn.partner.org/img/u1.png";
        assertThat(resolver.resolve(httpAvatar)).isEqualTo(httpAvatar);
    }

    @Test
    @DisplayName("Uses CDN base URL instead of backend base-url when configured")
    void resolve_withCdnBaseUrl_usesCdn() {
        AvatarUrlResolver cdnResolver = new AvatarUrlResolver(
                "https://api.example.com",
                "/uploads",
                "https://d111111abcdef8.cloudfront.net/"
        );

        assertThat(cdnResolver.resolve("/avatars/user_123.jpg"))
                .isEqualTo("https://d111111abcdef8.cloudfront.net/avatars/user_123.jpg");
        assertThat(cdnResolver.resolve("https://avatars.githubusercontent.com/u/1?v=4"))
                .isEqualTo("https://avatars.githubusercontent.com/u/1?v=4");
    }

    @Test
    @DisplayName("normalizeForStorage strips self-domain prefix down to domain-free relative path")
    void normalizeForStorage_stripsOwnDomainPrefix() {
        assertThat(resolver.normalizeForStorage("https://api.example.com/uploads/avatars/u1_100.jpg"))
                .isEqualTo("/avatars/u1_100.jpg");
        assertThat(resolver.normalizeForStorage("/uploads/avatars/u1_100.jpg"))
                .isEqualTo("/avatars/u1_100.jpg");
        assertThat(resolver.normalizeForStorage("/avatars/u1_100.jpg"))
                .isEqualTo("/avatars/u1_100.jpg");
    }

    @Test
    @DisplayName("normalizeForStorage keeps external URLs unchanged")
    void normalizeForStorage_keepsExternalUrl() {
        String ext = "https://avatars.githubusercontent.com/u/42?v=4";
        assertThat(resolver.normalizeForStorage(ext)).isEqualTo(ext);
    }

    @Test
    @DisplayName("normalizeForStorage rejects base64 data URIs and unsafe paths")
    void normalizeForStorage_rejectsBase64AndTraversal() {
        assertThatThrownBy(() -> resolver.normalizeForStorage("data:image/png;base64,iVBORw0KGgo="))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Base64");

        assertThatThrownBy(() -> resolver.normalizeForStorage("/avatars/../secrets.txt"))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> resolver.normalizeForStorage("javascript:alert(1)"))
                .isInstanceOf(AppException.class);
    }
}
