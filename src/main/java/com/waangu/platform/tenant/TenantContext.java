package com.waangu.platform.tenant;

/**
 * Immutable record holding tenant context for the current request.
 * <p>
 * Populated by {@link com.waangu.platform.filter.TenantContextFilter} from JWT claims
 * and stored in {@link TenantContextHolder} for the duration of the request.
 * </p>
 *
 * @param tenantId Unique tenant identifier (UUID)
 * @param tenantMode Isolation mode: POOLED, SCHEMA, or DEDICATED_DB
 * @param legalEntityId Legal entity identifier within the tenant
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param locale Locale for internationalization (e.g., "en_US", "fr_FR")
 * @param subscriptionId Subscription identifier for billing
 * @param correlationId Request correlation ID for distributed tracing
 * @param userId User identifier from JWT
 * @param tenantSchema PostgreSQL schema name for SCHEMA mode (e.g., "tenant_abc123")
 */
public record TenantContext(
    String tenantId,
    String tenantMode,       // POOLED | SCHEMA | DEDICATED_DB
    String legalEntityId,
    String countryCode,
    String locale,
    String subscriptionId,
    String correlationId,
    String userId,
    String tenantSchema     // for SCHEMA mode: e.g. tenant_&lt;uuidNoDash&gt;
) {}
