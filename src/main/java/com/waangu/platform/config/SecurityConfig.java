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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Default security configuration for Waangu 360 SaaS microservices.
 * <p>
 * Provides FinTech-grade security defaults:
 * <ul>
 *   <li>HSTS (HTTP Strict Transport Security) with 1-year max-age + preload</li>
 *   <li>X-Frame-Options: DENY (clickjacking protection)</li>
 *   <li>Content-Security-Policy: restrictive policy</li>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>Stateless session management (no HttpSession)</li>
 *   <li>TRACE method blocked (security best practice)</li>
 *   <li>CORS configured for frontend integration</li>
 *   <li>OAuth2 JWT resource server authentication</li>
 * </ul>
 * </p>
 * <p>
 * <b>CSRF Note:</b> CSRF is disabled for API endpoints because:
 * <ol>
 *   <li>JWT tokens are sent via Authorization header, not cookies</li>
 *   <li>Browsers don't automatically attach Authorization headers cross-origin</li>
 *   <li>This is the standard approach for stateless REST APIs</li>
 * </ol>
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
                // CSRF: Disabled for stateless JWT API
                // Justification: JWT in Authorization header is not vulnerable to CSRF
                // because browsers don't automatically attach it cross-origin
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/copilot/**")
                )
                
                // CORS: Required for frontend SPA integration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Security Headers
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'none'; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "base-uri 'self'"
                                )
                        )
                        .contentTypeOptions(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults())
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=()")
                        )
                )
                
                // Session: Stateless for JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // Authorization
                .authorizeHttpRequests(auth -> auth
                        // Public health endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        
                        // Block TRACE method (security best practice)
                        .requestMatchers(HttpMethod.TRACE, "/**").denyAll()
                        
                        // OPTIONS must be allowed for CORS preflight!
                        // DO NOT block OPTIONS or CORS will break
                        
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                
                // OAuth2 JWT Resource Server
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                )
                
                .build();
    }

    /**
     * CORS configuration for frontend integration.
     * <p>
     * In production, configure allowed origins via application properties.
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (configure via properties in production)
        configuration.setAllowedOriginPatterns(List.of(
                "https://*.waangu.com",
                "http://localhost:*"  // Development only
        ));
        
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Correlation-Id",
                "X-Trace-Id",
                "X-Idempotency-Key",
                "X-Legal-Entity-Id"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
                "X-Correlation-Id",
                "X-Trace-Id"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
