package com.onesignal.platform.common.security;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    @Getter
    @Builder
    public static class TenantContext {
        private final UUID userId;
        private final UUID organizationId;
        private final UUID appId;
        private final boolean superAdmin;
    }

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getContext() {
        return CONTEXT.get();
    }

    public static UUID getOrganizationId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getOrganizationId() : null;
    }

    public static UUID getAppId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getAppId() : null;
    }

    public static UUID getUserId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static boolean isSuperAdmin() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null && ctx.isSuperAdmin();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
