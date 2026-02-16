package com.waangu.platform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service implementing the transactional outbox pattern for reliable event publishing.
 * <p>
 * Events are written to the outbox table within the same transaction as the business
 * operation, ensuring atomicity. A separate process polls the outbox table and publishes
 * events to the message broker, guaranteeing at-least-once delivery.
 * </p>
 */
@Service
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void emit(String eventType, Map<String, Object> payload) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) throw new IllegalStateException("Missing TenantContext");

        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID legalEntityId = ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()
                ? UUID.fromString(ctx.legalEntityId()) : tenantId;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        jdbc.update("""
                INSERT INTO outbox_event(id, tenant_id, legal_entity_id, event_type, payload, status, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, 'NEW', now())
                """, UUID.randomUUID(), tenantId, legalEntityId, eventType, payloadJson);
    }
}
