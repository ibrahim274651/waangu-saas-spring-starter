package com.waangu.platform.tenant;

/**
 * Thread-local holder for {@link TenantContext}.
 * <p>
 * Provides thread-safe access to the current request's tenant context.
 * The context is automatically set by {@link com.waangu.platform.filter.TenantContextFilter}
 * and cleared at the end of each request.
 * </p>
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
