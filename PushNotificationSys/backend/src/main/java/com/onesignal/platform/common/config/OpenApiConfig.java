package com.onesignal.platform.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pushPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multi-Tenant Push Notification Platform API")
                        .description("Enterprise-grade push notification infrastructure supporting FCM (HTTP v1), Apple APNs (HTTP/2), Web Push (RFC 8291/8292), and persistent bidirectional WebSocket gateways.")
                        .version("1.0.0")
                        .contact(new Contact().name("Platform Engineering Team").email("engineering@platform.local"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth")
                        .addList("serverApiKeyAuth")
                        .addList("sdkApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token for Admin Portal APIs"))
                        .addSecuritySchemes("serverApiKeyAuth", new SecurityScheme()
                                .name("X-Api-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Server REST API Key for backend notification dispatch"))
                        .addSecuritySchemes("sdkApiKeyAuth", new SecurityScheme()
                                .name("X-Sdk-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Public Client SDK Key for device registration and event tracking")));
    }
}
