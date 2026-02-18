package com.waangu.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import com.waangu.platform.util.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service implementing the transactional outbox pattern for reliable event publishing.
 * <p>
 * Events are written to the outbox table within the same transaction as the business
 * operation, ensuring atomicity. A separate process polls the outbox table and publishes
 * events to the message broker, guaranteeing at-least-once delivery.
 * </p>
 * <p>
 * <b>IMPORTANT:</b> This method must be called within an existing @Transactional context
 * to ensure atomicity with the business operation. The propagation is set to MANDATORY
 * to enforce this requirement.
 * </p>
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Emits an event to the outbox table.
     * <p>
     * This method MUST be called within an existing @Transactional context
     * to ensure atomicity with the business operation.
     * </p>
     *
     * @param eventType The type of event (e.g., "stock.item_created")
     * @param payload   The event payload
     * @throws IllegalStateException if called outside a transaction or without TenantContext
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void emit(String eventType, Map<String, Object> payload) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            log.error("Attempted to emit outbox event without TenantContext");
            throw new IllegalStateException("Missing TenantContext");
        }

        log.debug("Emitting outbox event: type={}", eventType);

        UUID tenantId = UuidUtils.parseStrict(ctx.tenantId(), "tenantId");
        UUID legalEntityId = UuidUtils.parseOrDefault(ctx.legalEntityId(), tenantId);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload: {}", e.getMessage());
            throw new OutboxSerializationException("Failed to serialize outbox payload", e);
        }

        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event(id, tenant_id, legal_entity_id, event_type, payload, status, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, 'NEW', now())
                """, eventId, tenantId, legalEntityId, eventType, payloadJson);

        log.info("Outbox event emitted: type={}, id={}, tenantId={}", eventType, eventId, tenantId);
    }

    /**
     * Exception thrown when outbox payload serialization fails.
     */
    public static class OutboxSerializationException extends RuntimeException {
        public OutboxSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
