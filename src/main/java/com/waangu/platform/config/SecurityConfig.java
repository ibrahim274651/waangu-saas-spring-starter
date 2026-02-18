package com.waangu.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default security configuration for Waangu 360 SaaS microservices.
 * <p>
 * Provides FinTech-grade security defaults:
 * <ul>
 *   <li>HSTS (HTTP Strict Transport Security) with 1-year max-age</li>
 *   <li>X-Frame-Options: DENY (clickjacking protection)</li>
 *   <li>Content-Security-Policy: default-src 'none'</li>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>Stateless session management (no HttpSession)</li>
 *   <li>TRACE and OPTIONS methods blocked</li>
 *   <li>OAuth2 JWT resource server authentication</li>
 * </ul>
 * </p>
 * <p>
 * Consuming applications can override this configuration by defining their own
 * {@link SecurityFilterChain} bean.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Default security filter chain with FinTech-grade security headers.
     * <p>
     * This bean is only created if no other SecurityFilterChain is defined,
     * allowing consuming applications to provide their own configuration.
     * </p>
     *
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain waanguSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; frame-ancestors 'none'")
                        )
                        .contentTypeOptions(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults())
                )
                
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.TRACE, "/**").denyAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").denyAll()
                        .anyRequest().authenticated()
                )
                
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                )
                
                .build();
    }
}
