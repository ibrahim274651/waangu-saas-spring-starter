package com.waangu.platform.support;

import com.waangu.platform.guard.PiiGuard;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for chat support context integration.
 * <p>
 * Provides a standardized endpoint for the Waangu chat widget (Freshchat-like)
 * to retrieve contextual information about the current user session without
 * exposing any PII (Personally Identifiable Information).
 * </p>
 * <p>
 * The context includes:
 * <ul>
 *   <li>Tenant and legal entity identifiers</li>
 *   <li>Current module and screen</li>
 *   <li>Entity reference for deep-linking</li>
 *   <li>Correlation/trace ID for request tracing</li>
 *   <li>Severity level for support prioritization</li>
 * </ul>
 * </p>
 * <p>
 * All responses are validated by {@link PiiGuard} to ensure no sensitive
 * data is leaked to the chat system.
 * </p>
 */
@RestController
@RequestMapping("/api/support")
public class SupportContextController {

    private final PiiGuard piiGuard;

    public SupportContextController(PiiGuard piiGuard) {
        this.piiGuard = piiGuard;
    }

    /**
     * Returns contextual information for chat support integration.
     * <p>
     * This endpoint is designed to be called by the chat widget to provide
     * support agents with relevant context about the user's current session.
     * </p>
     *
     * @param module    The current ERP module (e.g., "ERP_STOCK", "ERP_TREASURY")
     * @param screen    Optional screen identifier (e.g., "product_edit", "journal_entry")
     * @param entityRef Optional entity reference for deep-linking (e.g., "PRODUCT:uuid", "ENTRY:uuid")
     * @param severity  Support severity level (NORMAL, HIGH, CRITICAL). Defaults to NORMAL.
     * @return Context map with tenant info, module, screen, entity ref, trace ID, and severity
     */
    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> context(
            @RequestParam String module,
            @RequestParam(required = false) String screen,
            @RequestParam(required = false) String entityRef,
            @RequestParam(defaultValue = "NORMAL") String severity
    ) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", "MISSING_CONTEXT",
                    "messageKey", "error.missing_tenant_context"
            ));
        }

        String traceId = MDC.get("correlation_id");
        if (traceId == null) {
            traceId = MDC.get("trace_id");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("tenant_id", ctx.tenantId());
        out.put("legal_entity_id", ctx.legalEntityId() != null ? ctx.legalEntityId() : "");
        out.put("country_code", ctx.countryCode());
        out.put("module", module);
        out.put("screen", screen != null ? screen : "");
        out.put("entity_ref", entityRef != null ? entityRef : "");
        out.put("trace_id", traceId != null ? traceId : "");
        out.put("severity", severity);
        out.put("billing_status", ctx.subscriptionId() != null ? "ACTIVE" : "UNKNOWN");
        out.put("locale", ctx.locale());

        piiGuard.assertNoPII(out);

        return ResponseEntity.ok(out);
    }
}
