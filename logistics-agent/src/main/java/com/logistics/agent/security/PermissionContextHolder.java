package com.logistics.agent.security;

import java.util.Optional;

public final class PermissionContextHolder {

    private static final ThreadLocal<PermissionContext> CONTEXT = new ThreadLocal<>();

    private PermissionContextHolder() {
    }

    public static void set(PermissionContext context) {
        CONTEXT.set(context);
    }

    public static Optional<PermissionContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
