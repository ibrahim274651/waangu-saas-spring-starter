package com.waangu.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import com.waangu.platform.util.UuidUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for writing append-only audit logs with cryptographic hash chaining.
 * <p>
 * Provides tamper-evident audit trails by linking each log entry to the previous one
 * via SHA-256 hash chains. This ensures compliance with financial regulations and
 * standards such as OPC (Ordonnance sur la Protection des Consommateurs) and ISA
 * (International Standards on Auditing).
 * </p>
 * <p>
 * Each audit entry includes:
 * <ul>
 *   <li>Tenant and legal entity isolation</li>
 *   <li>Actor user identification</li>
 *   <li>Action and entity details</li>
 *   <li>Correlation ID for request tracing</li>
 *   <li>Hash chain linking (prev_hash → curr_hash)</li>
 * </ul>
 * </p>
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void write(String action, String entityType, UUID entityId, Map<String, Object> payload) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            log.error("Attempted to write audit log without TenantContext");
            throw new IllegalStateException("Missing TenantContext");
        }

        log.debug("Writing audit log: action={}, entityType={}, entityId={}", action, entityType, entityId);

        UUID tenantId = UuidUtils.parseStrict(ctx.tenantId(), "tenantId");
        UUID legalEntityId = UuidUtils.parseOrDefault(ctx.legalEntityId(), tenantId);
        UUID actorUserId = UuidUtils.parseOrGenerate(ctx.userId());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit payload: {}", e.getMessage());
            throw new AuditSerializationException("Failed to serialize audit payload", e);
        }

        // Use FOR UPDATE to prevent race conditions in hash chain
        var rows = jdbc.queryForList(
                "SELECT curr_hash FROM audit_log WHERE tenant_id = ? AND legal_entity_id = ? ORDER BY occurred_at DESC LIMIT 1 FOR UPDATE",
                tenantId, legalEntityId
        );
        String prevHash = rows.isEmpty() ? null : (String) rows.get(0).get("curr_hash");

        String currHash = DigestUtils.sha256Hex(
                (prevHash != null ? prevHash : "") + "|" + action + "|" + entityType + "|" + entityId + "|" + payloadJson + "|" + ctx.correlationId()
        );

        jdbc.update("""
                INSERT INTO audit_log(id, tenant_id, legal_entity_id, actor_user_id, action, entity_type, entity_id, correlation_id, payload, occurred_at, prev_hash, curr_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), ?, ?)
                """,
                UUID.randomUUID(), tenantId, legalEntityId, actorUserId, action, entityType, entityId,
                ctx.correlationId(), payloadJson, prevHash, currHash
        );

        log.info("Audit log written: action={}, entityType={}, tenantId={}", action, entityType, tenantId);
    }

    /**
     * Exception thrown when audit payload serialization fails.
     */
    public static class AuditSerializationException extends RuntimeException {
        public AuditSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
