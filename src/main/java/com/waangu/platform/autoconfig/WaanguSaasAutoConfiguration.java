package com.waangu.platform.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waangu.platform.audit.AuditLogService;
import com.waangu.platform.db.DbSessionInitializer;
import com.waangu.platform.filter.CorrelationIdFilter;
import com.waangu.platform.filter.ForbiddenBodyFieldsFilter;
import com.waangu.platform.filter.TenantContextFilter;
import com.waangu.platform.guard.PiiGuard;
import com.waangu.platform.idempotency.IdempotencyService;
import com.waangu.platform.outbox.OutboxService;
import com.waangu.platform.support.SupportContextController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring Boot auto-configuration for Waangu 360 SaaS multi-tenant infrastructure.
 * <p>
 * Automatically registers and configures essential filters in the correct order:
 * <ol>
 *   <li>CorrelationIdFilter (HIGHEST_PRECEDENCE) - Ensures correlation/trace ID on all requests</li>
 *   <li>TenantContextFilter (HIGHEST_PRECEDENCE + 10) - Extracts and validates tenant context from JWT</li>
 *   <li>ForbiddenBodyFieldsFilter (HIGHEST_PRECEDENCE + 20) - Blocks forbidden fields in request bodies</li>
 * </ol>
 * </p>
 * <p>
 * Also provides:
 * <ul>
 *   <li>SecurityConfig - HSTS, CSP, and OAuth2 JWT resource server</li>
 *   <li>PiiGuard - Prevents PII leakage in support context</li>
 *   <li>SupportContextController - Chat support integration endpoint</li>
 * </ul>
 * </p>
 */
@AutoConfiguration(after = org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class)
@ConditionalOnWebApplication
public class WaanguSaasAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>(new CorrelationIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "tenantContextFilter")
    @ConditionalOnProperty(name = "waangu.tenant-filter.enabled", havingValue = "true", matchIfMissing = false)
    public TenantContextFilter tenantContextFilter(
            @Value("${waangu.module-id:}") String moduleId) {
        return new TenantContextFilter(moduleId.isBlank() ? null : moduleId);
    }

    @Bean
    @ConditionalOnMissingBean(name = "tenantContextFilterRegistration")
    @ConditionalOnProperty(name = "waangu.tenant-filter.enabled", havingValue = "true", matchIfMissing = false)
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.addUrlPatterns("/api/*", "/copilot/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ForbiddenBodyFieldsFilter> forbiddenBodyFieldsFilter() {
        FilterRegistrationBean<ForbiddenBodyFieldsFilter> bean = new FilterRegistrationBean<>(new ForbiddenBodyFieldsFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiGuard piiGuard() {
        return new PiiGuard();
    }

    @Bean
    @ConditionalOnMissingBean
    public SupportContextController supportContextController(PiiGuard piiGuard) {
        return new SupportContextController(piiGuard);
    }

    // ==================== Platform Services ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public AuditLogService auditLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new AuditLogService(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public IdempotencyService idempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new IdempotencyService(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public OutboxService outboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new OutboxService(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public DbSessionInitializer dbSessionInitializer(JdbcTemplate jdbcTemplate) {
        return new DbSessionInitializer(jdbcTemplate);
    }
}
