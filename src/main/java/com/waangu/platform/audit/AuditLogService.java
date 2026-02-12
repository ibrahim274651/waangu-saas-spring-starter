package com.waangu.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit log with hash chaining (chagpt — OPC / ISA ready).
 */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(String action, String entityType, UUID entityId, Map<String, Object> payload) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) throw new IllegalStateException("Missing TenantContext");

        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID legalEntityId = ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()
                ? UUID.fromString(ctx.legalEntityId()) : tenantId;
        UUID actorUserId = ctx.userId() != null && !ctx.userId().isBlank()
                ? UUID.fromString(ctx.userId()) : UUID.randomUUID();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        var rows = jdbc.queryForList(
                "SELECT curr_hash FROM audit_log WHERE tenant_id = ? AND legal_entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
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
    }
}
