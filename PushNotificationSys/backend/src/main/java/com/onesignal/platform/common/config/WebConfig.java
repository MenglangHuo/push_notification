package com.onesignal.platform.common.config;

import com.onesignal.platform.common.storage.LocalDiskStorageBackend;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Configures static file serving for {@code /uploads/**} when using local disk storage,
 * and adds strict security headers (nosniff, CSP, CORP) to uploaded assets.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<LocalDiskStorageBackend> localDiskStorageProvider;

    @Value("${app.upload.public-path-prefix:/uploads}")
    private String publicPathPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        LocalDiskStorageBackend localBackend = localDiskStorageProvider.getIfAvailable();
        if (localBackend == null) {
            return;
        }

        String prefix = normalizePrefix(publicPathPrefix);
        String uploadLocation = localBackend.getUploadDir().toUri().toString();
        if (!uploadLocation.endsWith("/")) {
            uploadLocation += "/";
        }

        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(uploadLocation)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String prefix = normalizePrefix(publicPathPrefix);
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Content-Security-Policy", "default-src 'none'");
                response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
                return true;
            }
        }).addPathPatterns(prefix + "/**");
    }

    private String normalizePrefix(String raw) {
        String p = (raw == null || raw.isBlank()) ? "/uploads" : raw.trim();
        if (!p.startsWith("/")) p = "/" + p;
        while (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }
}
