package com.waangu.platform.autoconfig;

import com.waangu.platform.filter.CorrelationIdFilter;
import com.waangu.platform.filter.ForbiddenBodyFieldsFilter;
import com.waangu.platform.filter.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for Waangu 360 SaaS multi-tenant infrastructure.
 * <p>
 * Automatically registers and configures essential filters in the correct order:
 * <ol>
 *   <li>CorrelationIdFilter (HIGHEST_PRECEDENCE) - Ensures correlation ID on all requests</li>
 *   <li>TenantContextFilter (HIGHEST_PRECEDENCE + 10) - Extracts and validates tenant context from JWT</li>
 *   <li>ForbiddenBodyFieldsFilter (HIGHEST_PRECEDENCE + 20) - Blocks forbidden fields in request bodies</li>
 * </ol>
 * </p>
 */
@AutoConfiguration
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
    public TenantContextFilter tenantContextFilter(
            @Value("${waangu.module-id:}") String moduleId) {
        return new TenantContextFilter(moduleId.isBlank() ? null : moduleId);
    }

    @Bean
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
}
