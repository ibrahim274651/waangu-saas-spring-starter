package com.waangu.platform.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import com.waangu.platform.util.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes an action with idempotency guarantee.
     *
     * @param key         The idempotency key (typically from X-Idempotency-Key header)
     * @param requestHash SHA-256 hash of the request payload for conflict detection
     * @param action      The action to execute
     * @param <T>         The return type
     * @return The result of the action (or cached result if key was already used)
     * @throws ResponseStatusException if key is missing or reused with different request
     */
    @Transactional
    public <T> T withIdempotency(String key, String requestHash, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            log.warn("Idempotency key missing in request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }

        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            log.error("Attempted idempotent operation without TenantContext");
            throw new IllegalStateException("Missing TenantContext");
        }

        log.debug("Processing idempotent request: key={}", key);

        UUID tenantId = UuidUtils.parseStrict(ctx.tenantId(), "tenantId");
        UUID legalEntityId = UuidUtils.parseOrDefault(ctx.legalEntityId(), tenantId);

        // Check for existing idempotency record
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT response, request_hash FROM idempotency_key
                WHERE tenant_id = ? AND legal_entity_id = ? AND key = ?
                FOR UPDATE
                """, tenantId, legalEntityId, key);

        if (!rows.isEmpty()) {
            Object resp = rows.get(0).get("response");
            String prevHash = (String) rows.get(0).get("request_hash");
            
            if (!Objects.equals(prevHash, requestHash)) {
                log.warn("Idempotency key reused with different request: key={}", key);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE_WITH_DIFFERENT_REQUEST");
            }
            
            if (resp != null) {
                log.info("Returning cached response for idempotency key: key={}", key);
                return objectMapper.convertValue(resp, new TypeReference<T>() {});
            }
        }

        // Insert new idempotency record
        jdbc.update("""
                INSERT INTO idempotency_key(id, tenant_id, legal_entity_id, key, request_hash, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', now())
                ON CONFLICT (tenant_id, legal_entity_id, key) DO NOTHING
                """, UUID.randomUUID(), tenantId, legalEntityId, key, requestHash);

        // Execute the action
        T result;
        try {
            result = action.get();
        } catch (Exception e) {
            // Mark as failed
            jdbc.update("""
                    UPDATE idempotency_key SET status = 'FAILED'
                    WHERE tenant_id = ? AND legal_entity_id = ? AND key = ?
                    """, tenantId, legalEntityId, key);
            throw e;
        }

        // Store the result
        try {
            jdbc.update("""
                    UPDATE idempotency_key SET response = ?::jsonb, status = 'COMPLETED'
                    WHERE tenant_id = ? AND legal_entity_id = ? AND key = ?
                    """, objectMapper.writeValueAsString(result), tenantId, legalEntityId, key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotency response: {}", e.getMessage());
            throw new IdempotencySerializationException("Failed to serialize idempotency response", e);
        }

        log.info("Idempotent operation completed: key={}", key);
        return result;
    }

    /**
     * Exception thrown when idempotency response serialization fails.
     */
    public static class IdempotencySerializationException extends RuntimeException {
        public IdempotencySerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
