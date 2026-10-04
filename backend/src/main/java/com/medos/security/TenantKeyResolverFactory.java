package com.medos.security;

/**
 * Static bridge between the global {@code TenantKeyResolver} and code that cannot
 * receive it as a constructor dependency.
 *
 * <p>{@code EncryptionUtil} is a JPA {@code AttributeConverter}, which Hibernate
 * instantiates itself. It therefore reaches keys through this factory rather than
 * through injection. The factory delegates to the process-wide {@link TenantKeyHolder},
 * which Spring configures at startup.
 *
 * <p>Tests use {@link #setInstance(TenantKeyResolver)} to install an in-memory
 * resolver, so the same converter path is exercised without a database.
 */
public final class TenantKeyResolverFactory {

    private static volatile TenantKeyResolver resolver;

    private TenantKeyResolverFactory() {}

    public static TenantKeyResolver get() {
        TenantKeyResolver local = resolver;
        if (local == null) {
            throw new IllegalStateException(
                    "TenantKeyResolver is not initialised. "
                            + "Call EncryptionUtil.init() at startup, or set a test resolver.");
        }
        return local;
    }

    public static boolean isInitialised() {
        return resolver != null;
    }

    public static void setInstance(TenantKeyResolver resolver) {
        TenantKeyResolverFactory.resolver = resolver;
    }

    public static void reset() {
        resolver = null;
    }
}