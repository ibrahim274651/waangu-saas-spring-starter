package com.waangu.platform.tenant;

/**
 * Thread-local holder for TenantContext (chagpt).
 */
public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CTX = new ThreadLocal<>();

    public static TenantContext get() {
        return CTX.get();
    }

    public static void set(TenantContext ctx) {
        CTX.set(ctx);
    }

    public static void clear() {
        CTX.remove();
    }

    private TenantContextHolder() {}
}
