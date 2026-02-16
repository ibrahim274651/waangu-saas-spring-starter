package com.waangu.platform.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service for ensuring idempotency of critical write operations.
 * <p>
 * Prevents duplicate processing of financial transactions (double-spend prevention)
 * by tracking idempotency keys and request hashes. If the same key is reused with
 * a different request payload, a conflict error is returned.
 * </p>
 * <p>
 * Usage pattern:
 * <pre>{@code
 * idempotencyService.withIdempotency(
 *     idempotencyKey,
 *     requestHash,
 *     () -> performFinancialOperation()
 * );
 * }</pre>
 * </p>
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
public class IdempotencyService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public <T> T withIdempotency(String key, String requestHash, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }

        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) throw new IllegalStateException("Missing TenantContext");

        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID legalEntityId = ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()
                ? UUID.fromString(ctx.legalEntityId()) : tenantId;

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT response, request_hash FROM idempotency_key
                WHERE tenant_id = ? AND legal_entity_id = ? AND key = ?
                """, tenantId, legalEntityId, key);

        if (!rows.isEmpty()) {
            Object resp = rows.get(0).get("response");
            String prevHash = (String) rows.get(0).get("request_hash");
            if (!Objects.equals(prevHash, requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE_WITH_DIFFERENT_REQUEST");
            }
            if (resp != null) {
                return objectMapper.convertValue(resp, new TypeReference<T>() {});
            }
        }

        jdbc.update("""
                INSERT INTO idempotency_key(id, tenant_id, legal_entity_id, key, request_hash, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id, legal_entity_id, key) DO NOTHING
                """, UUID.randomUUID(), tenantId, legalEntityId, key, requestHash);

        T result = action.get();

        try {
            jdbc.update("""
                    UPDATE idempotency_key SET response = ?::jsonb
                    WHERE tenant_id = ? AND legal_entity_id = ? AND key = ?
                    """, objectMapper.writeValueAsString(result), tenantId, legalEntityId, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
