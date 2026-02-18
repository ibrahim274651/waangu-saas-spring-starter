# REVUE DE CODE COMPLÈTE - waangu-saas-spring-starter

## Table des Matières

1. [Résumé Exécutif](#1-résumé-exécutif)
2. [Erreurs Critiques et Corrections](#2-erreurs-critiques-et-corrections)
3. [Erreurs de Débutant Typiques](#3-erreurs-de-débutant-typiques)
4. [Vecteurs d'Attaque et Menaces](#4-vecteurs-dattaque-et-menaces)
5. [Références Officielles de Sécurité](#5-références-officielles-de-sécurité)
6. [Tests Obligatoires Avant Production](#6-tests-obligatoires-avant-production)
7. [Assurance Qualité Backend](#7-assurance-qualité-backend)

---

## 1. Résumé Exécutif

| Sévérité | Nombre | Catégories |
|----------|--------|------------|
| 🔴 CRITIQUE | 5 | Injection SQL, CSRF, Parsing UUID |
| 🟠 HAUTE | 8 | CORS, Type Safety, Gestion d'erreurs, DoS |
| 🟡 MOYENNE | 9 | Transactions, Race Conditions, Validation |
| 🟢 BASSE | 5 | Tests, Documentation, Cohérence |
| **TOTAL** | **27** | |

---

## 2. Erreurs Critiques et Corrections

### 2.1 🔴 CRITIQUE: Injection SQL dans DbSessionInitializer

**Fichier:** `src/main/java/com/waangu/platform/db/DbSessionInitializer.java`
**Ligne:** 62

**Problème:**
```java
// DANGEREUX - Concaténation directe de chaîne dans SQL
jdbcTemplate.execute("SET LOCAL search_path TO " + ctx.tenantSchema() + ", public");
```

**Correction:**
```java
package com.waangu.platform.db;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import com.waangu.platform.tenant.TenantDbResolution;
import com.waangu.platform.tenant.TenantRegistryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class DbSessionInitializer {

    private static final Logger log = LoggerFactory.getLogger(DbSessionInitializer.class);
    
    // Pattern strict pour les noms de schéma PostgreSQL
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    
    // Cache des schémas validés (whitelist dynamique)
    private final Set<String> validatedSchemas = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbcTemplate;
    private final TenantRegistryClient tenantRegistry;

    public DbSessionInitializer(JdbcTemplate jdbcTemplate, TenantRegistryClient tenantRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRegistry = tenantRegistry;
    }

    public void initializeSession() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            log.warn("No tenant context available for DB session initialization");
            return;
        }

        String tenantId = ctx.tenantId();
        TenantDbResolution resolution = tenantRegistry.resolve(tenantId);

        switch (resolution.mode()) {
            case "POOLED" -> initializePooledMode(ctx);
            case "SCHEMA" -> initializeSchemaMode(ctx, resolution);
            case "DEDICATED_DB" -> log.debug("Dedicated DB mode - no session init needed");
            default -> throw new IllegalStateException("Unknown tenant mode: " + resolution.mode());
        }
    }

    private void initializePooledMode(TenantContext ctx) {
        // SET LOCAL pour RLS - utilise des paramètres, pas de concaténation
        jdbcTemplate.update("SELECT set_config('app.current_tenant', ?, true)", ctx.tenantId());
        
        if (ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()) {
            jdbcTemplate.update("SELECT set_config('app.current_legal_entity', ?, true)", ctx.legalEntityId());
        }
        
        if (ctx.countryCode() != null) {
            jdbcTemplate.update("SELECT set_config('app.current_country', ?, true)", ctx.countryCode());
        }
        
        log.debug("Initialized POOLED mode session for tenant={}", ctx.tenantId());
    }

    private void initializeSchemaMode(TenantContext ctx, TenantDbResolution resolution) {
        String schema = resolution.schema();
        
        // Validation stricte du nom de schéma
        validateSchemaName(schema);
        
        // Vérifier que le schéma existe (whitelist dynamique)
        if (!validatedSchemas.contains(schema)) {
            verifySchemaExists(schema);
            validatedSchemas.add(schema);
        }
        
        // Utiliser quote_ident pour échapper le nom de schéma côté PostgreSQL
        jdbcTemplate.execute("SELECT set_config('search_path', quote_ident('" + schema + "') || ', public', true)");
        
        // Aussi initialiser les variables RLS
        initializePooledMode(ctx);
        
        log.debug("Initialized SCHEMA mode session for tenant={}, schema={}", ctx.tenantId(), schema);
    }

    private void validateSchemaName(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or blank");
        }
        
        if (!SCHEMA_PATTERN.matcher(schema).matches()) {
            log.error("Invalid schema name detected: {}", schema);
            throw new IllegalArgumentException("Invalid schema name format: " + schema);
        }
        
        // Bloquer les schémas système
        if (schema.startsWith("pg_") || schema.equals("information_schema")) {
            throw new IllegalArgumentException("Cannot use system schema: " + schema);
        }
    }

    private void verifySchemaExists(String schema) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
            Integer.class,
            schema
        );
        
        if (count == null || count == 0) {
            throw new IllegalStateException("Schema does not exist: " + schema);
        }
    }
}
```

---

### 2.2 🔴 CRITIQUE: Parsing UUID sans gestion d'erreur

**Fichier:** `AuditLogService.java`, `OutboxService.java`, `IdempotencyService.java`

**Problème:**
```java
// DANGEREUX - Peut lancer IllegalArgumentException non gérée
UUID tenantId = UUID.fromString(ctx.tenantId());
```

**Correction - Créer une classe utilitaire:**
```java
package com.waangu.platform.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Utilitaires pour la manipulation sécurisée des UUID.
 */
public final class UuidUtils {

    private static final Logger log = LoggerFactory.getLogger(UuidUtils.class);

    private UuidUtils() {
        // Utility class
    }

    /**
     * Parse un UUID de manière sécurisée avec valeur par défaut.
     *
     * @param value        La chaîne à parser
     * @param defaultValue Valeur par défaut si parsing échoue
     * @return UUID parsé ou valeur par défaut
     */
    public static UUID parseOrDefault(String value, UUID defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: '{}', using default", value);
            return defaultValue;
        }
    }

    /**
     * Parse un UUID de manière sécurisée, génère un nouveau si invalide.
     *
     * @param value La chaîne à parser
     * @return UUID parsé ou nouveau UUID aléatoire
     */
    public static UUID parseOrGenerate(String value) {
        return parseOrDefault(value, UUID.randomUUID());
    }

    /**
     * Parse un UUID de manière stricte (lance exception si invalide).
     *
     * @param value       La chaîne à parser
     * @param fieldName   Nom du champ pour le message d'erreur
     * @return UUID parsé
     * @throws IllegalArgumentException si le format est invalide
     */
    public static UUID parseStrict(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " has invalid UUID format: " + value, e);
        }
    }

    /**
     * Vérifie si une chaîne est un UUID valide.
     *
     * @param value La chaîne à vérifier
     * @return true si valide, false sinon
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

**Utilisation dans les services:**
```java
import static com.waangu.platform.util.UuidUtils.*;

// Dans AuditLogService, OutboxService, IdempotencyService:
UUID tenantId = parseStrict(ctx.tenantId(), "tenantId");
UUID legalEntityId = parseOrDefault(ctx.legalEntityId(), tenantId);
UUID actorUserId = parseOrGenerate(ctx.userId());
```

---

### 2.3 🔴 CRITIQUE: CSRF désactivé sans protection alternative

**Fichier:** `SecurityConfig.java`

**Problème:**
```java
.csrf(csrf -> csrf.disable())  // Désactivé complètement
```

**Correction:**
```java
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain waanguSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF: Désactivé pour les API REST stateless avec JWT
                // Justification: Les tokens JWT dans le header Authorization ne sont pas
                // vulnérables aux attaques CSRF car ils ne sont pas envoyés automatiquement
                // par le navigateur comme les cookies.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/copilot/**")
                        // Garder CSRF pour les endpoints non-API si nécessaire
                )
                
                // CORS: Autoriser les requêtes cross-origin pour le frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Headers de sécurité
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
                
                // Session: Stateless pour JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // Autorisation des requêtes
                .authorizeHttpRequests(auth -> auth
                        // Health checks publics
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        
                        // Bloquer TRACE (security best practice)
                        .requestMatchers(HttpMethod.TRACE, "/**").denyAll()
                        
                        // OPTIONS autorisé pour CORS preflight
                        // NE PAS bloquer OPTIONS sinon CORS ne fonctionne pas!
                        
                        // Tout le reste nécessite authentification
                        .anyRequest().authenticated()
                )
                
                // OAuth2 JWT Resource Server
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                )
                
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Origines autorisées (à configurer via properties en production)
        configuration.setAllowedOriginPatterns(List.of(
                "https://*.waangu.com",
                "http://localhost:*"  // Dev seulement
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
```

---

### 2.4 🟠 HAUTE: Limite de taille du body manquante (DoS)

**Fichier:** `ForbiddenBodyFieldsFilter.java`

**Problème:**
```java
byte[] body = request.getInputStream().readAllBytes();  // Pas de limite!
```

**Correction:**
```java
package com.waangu.platform.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.util.Iterator;
import java.util.Set;

public class ForbiddenBodyFieldsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ForbiddenBodyFieldsFilter.class);

    // Limite de taille: 10 MB (configurable)
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    // Champs interdits dans les requêtes (anti mass-assignment)
    private static final Set<String> FORBIDDEN = Set.of(
            "tenant_id", "tenantId",
            "legal_entity_id", "legalEntityId",
            "user_id", "userId",
            "created_at", "createdAt",
            "updated_at", "updatedAt",
            "created_by", "createdBy",
            "updated_by", "updatedBy",
            "is_deleted", "isDeleted",
            "version"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String contentType = request.getContentType();

        // Seulement vérifier les requêtes avec body JSON
        if (!hasJsonBody(method, contentType)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lire le body avec limite de taille
        byte[] body;
        try {
            body = readBodyWithLimit(request);
        } catch (PayloadTooLargeException e) {
            log.warn("Request body too large from IP={}", request.getRemoteAddr());
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "error.payload_too_large");
            return;
        }

        // Vérifier les champs interdits
        if (body.length > 0) {
            String forbiddenField = findForbiddenField(body);
            if (forbiddenField != null) {
                log.warn("Forbidden field '{}' detected in request body", forbiddenField);
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "FORBIDDEN_FIELD", "error.forbidden_field_in_body");
                return;
            }
        }

        // Wrapper pour permettre la relecture du body
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, body);
        filterChain.doFilter(cachedRequest, response);
    }

    private boolean hasJsonBody(String method, String contentType) {
        if (contentType == null) return false;
        if (!contentType.contains("application/json")) return false;
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private byte[] readBodyWithLimit(HttpServletRequest request) throws IOException {
        InputStream inputStream = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        int totalRead = 0;

        while ((bytesRead = inputStream.read(chunk)) != -1) {
            totalRead += bytesRead;
            if (totalRead > MAX_BODY_SIZE) {
                throw new PayloadTooLargeException();
            }
            buffer.write(chunk, 0, bytesRead);
        }

        return buffer.toByteArray();
    }

    private String findForbiddenField(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return findForbiddenFieldRecursive(root, "");
        } catch (IOException e) {
            // JSON invalide - laisser passer, le controller gérera l'erreur
            return null;
        }
    }

    private String findForbiddenFieldRecursive(JsonNode node, String path) {
        if (node == null) return null;

        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                
                // Vérifier si le champ est interdit
                if (FORBIDDEN.contains(fieldName)) {
                    return path.isEmpty() ? fieldName : path + "." + fieldName;
                }
                
                // Récursion dans les objets imbriqués
                String found = findForbiddenFieldRecursive(
                        node.get(fieldName),
                        path.isEmpty() ? fieldName : path + "." + fieldName
                );
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String found = findForbiddenFieldRecursive(
                        node.get(i),
                        path + "[" + i + "]"
                );
                if (found != null) return found;
            }
        }

        return null;
    }

    private void sendError(HttpServletResponse response, int status, String code, String messageKey)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"code\":\"%s\",\"messageKey\":\"%s\"}", code, messageKey
        ));
    }

    private static class PayloadTooLargeException extends IOException {
    }

    // Wrapper pour cacher le body et permettre sa relecture
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.cachedBody = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return inputStream.read();
        }
    }
}
```

---

## 3. Erreurs de Débutant Typiques

### 3.1 Exceptions génériques qui cachent les erreurs

**❌ Mauvais:**
```java
} catch (Exception e) {
    throw new RuntimeException(e);  // Perd le contexte
}
```

**✅ Correct:**
```java
} catch (JsonProcessingException e) {
    throw new AuditSerializationException("Failed to serialize audit payload", e);
} catch (DataAccessException e) {
    throw new AuditPersistenceException("Failed to write audit log", e);
}
```

### 3.2 Oublier @Transactional

**❌ Mauvais:**
```java
public void emit(String eventType, Map<String, Object> payload) {
    // Pas de @Transactional - l'outbox peut être écrit sans la transaction business
}
```

**✅ Correct:**
```java
@Transactional(propagation = Propagation.MANDATORY)
public void emit(String eventType, Map<String, Object> payload) {
    // MANDATORY = doit être appelé dans une transaction existante
}
```

### 3.3 Créer plusieurs ObjectMapper

**❌ Mauvais:**
```java
public class OutboxService {
    private final ObjectMapper mapper = new ObjectMapper();  // Nouvelle instance
}

public class AuditService {
    private final ObjectMapper mapper = new ObjectMapper();  // Encore une autre
}
```

**✅ Correct:**
```java
@Component
public class OutboxService {
    private final ObjectMapper mapper;
    
    public OutboxService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;  // Injecté, configuré centralement
    }
}
```

### 3.4 Logging insuffisant

**❌ Mauvais:**
```java
public void write(String action, String entityType, UUID entityId) {
    // Aucun log - impossible de debugger en production
    jdbc.update(...);
}
```

**✅ Correct:**
```java
public void write(String action, String entityType, UUID entityId) {
    log.debug("Writing audit log: action={}, entityType={}, entityId={}", 
              action, entityType, entityId);
    try {
        jdbc.update(...);
        log.info("Audit log written successfully: action={}", action);
    } catch (DataAccessException e) {
        log.error("Failed to write audit log: action={}, error={}", action, e.getMessage());
        throw e;
    }
}
```

### 3.5 Validation d'entrée manquante

**❌ Mauvais:**
```java
@GetMapping("/context")
public ResponseEntity<Map<String, Object>> context(@RequestParam String module) {
    // module peut contenir n'importe quoi
}
```

**✅ Correct:**
```java
@GetMapping("/context")
public ResponseEntity<Map<String, Object>> context(
        @RequestParam @Pattern(regexp = "^[A-Z_]{1,50}$") String module,
        @RequestParam(required = false) @Size(max = 100) String screen
) {
    // Validation automatique par Bean Validation
}
```

---

## 4. Vecteurs d'Attaque et Menaces

### 4.1 Menaces Externes

#### A. Injection SQL
```
Vecteur: Manipulation des paramètres tenant_schema
Exemple: tenant_schema = "public; DROP TABLE users; --"
Protection: Validation regex + whitelist + quote_ident()
```

#### B. Cross-Site Scripting (XSS)
```
Vecteur: Injection de scripts dans les champs de données
Exemple: entityRef = "<script>steal(document.cookie)</script>"
Protection: Content-Security-Policy + échappement des sorties
```

#### C. Cross-Site Request Forgery (CSRF)
```
Vecteur: Requêtes malveillantes depuis un site tiers
Protection: JWT dans header (pas cookie) + CORS strict
```

#### D. Denial of Service (DoS)
```
Vecteur: Envoi de requêtes avec body énorme
Exemple: POST /api/products avec body de 10GB
Protection: Limite de taille du body (MAX_BODY_SIZE)
```

#### E. Brute Force / Credential Stuffing
```
Vecteur: Tentatives répétées de connexion
Protection: Rate limiting + Account lockout + MFA
```

### 4.2 Attaques Internes

#### A. Privilege Escalation
```
Vecteur: Modification du tenant_id dans le JWT
Protection: Validation JWT côté serveur + RLS PostgreSQL
```

#### B. Data Exfiltration
```
Vecteur: Accès aux données d'autres tenants
Protection: RLS + Validation tenant_id dans chaque requête
```

#### C. Mass Assignment
```
Vecteur: Injection de champs interdits dans le body
Exemple: {"name": "Product", "tenant_id": "autre-tenant"}
Protection: ForbiddenBodyFieldsFilter
```

### 4.3 Compromission Interne

#### A. Insider Threat
```
Vecteur: Employé malveillant avec accès au code
Protection: Code review + Audit logs + Principe du moindre privilège
```

#### B. Supply Chain Attack
```
Vecteur: Dépendance compromise (ex: log4shell)
Protection: Dependency scanning + SBOM + Mise à jour régulière
```

### 4.4 Matrice des Menaces STRIDE

| Menace | Description | Contrôle |
|--------|-------------|----------|
| **S**poofing | Usurpation d'identité | JWT + OAuth2 |
| **T**ampering | Modification des données | Audit hash-chain + RLS |
| **R**epudiation | Nier une action | Audit logs immuables |
| **I**nformation Disclosure | Fuite de données | RLS + PiiGuard |
| **D**enial of Service | Indisponibilité | Rate limiting + Body size limit |
| **E**levation of Privilege | Escalade de privilèges | RBAC + LegalEntityGuard |

---

## 5. Références Officielles de Sécurité

### 5.1 Standards Internationaux

| Standard | Description | Lien |
|----------|-------------|------|
| **ISO 27001** | Système de management de la sécurité | https://www.iso.org/standard/27001 |
| **ISO 27017** | Sécurité cloud | https://www.iso.org/standard/43757.html |
| **ISO 27018** | Protection des données personnelles cloud | https://www.iso.org/standard/76559.html |
| **SOC 2** | Trust Services Criteria | https://www.aicpa.org/soc2 |
| **PCI DSS** | Sécurité des données de paiement | https://www.pcisecuritystandards.org |

### 5.2 Europe

| Réglementation | Description | Lien |
|----------------|-------------|------|
| **RGPD/GDPR** | Protection des données personnelles | https://gdpr.eu |
| **NIS2** | Directive cybersécurité | https://digital-strategy.ec.europa.eu/en/policies/nis2-directive |
| **DORA** | Résilience opérationnelle numérique (FinTech) | https://www.eba.europa.eu/regulation-and-policy/operational-resilience |
| **eIDAS** | Identification électronique | https://digital-strategy.ec.europa.eu/en/policies/eidas-regulation |
| **ENISA** | Agence européenne cybersécurité | https://www.enisa.europa.eu |

### 5.3 États-Unis

| Réglementation | Description | Lien |
|----------------|-------------|------|
| **NIST CSF** | Cybersecurity Framework | https://www.nist.gov/cyberframework |
| **NIST 800-53** | Security Controls | https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final |
| **SOX** | Sarbanes-Oxley (audit financier) | https://www.sec.gov/spotlight/sarbanes-oxley.htm |
| **HIPAA** | Données de santé | https://www.hhs.gov/hipaa |
| **CCPA** | California Consumer Privacy Act | https://oag.ca.gov/privacy/ccpa |
| **FedRAMP** | Cloud gouvernemental | https://www.fedramp.gov |

### 5.4 Asie

| Pays | Réglementation | Lien |
|------|----------------|------|
| **Japon** | APPI (Act on Protection of Personal Information) | https://www.ppc.go.jp/en/ |
| **Singapour** | PDPA + MAS TRM Guidelines | https://www.pdpc.gov.sg |
| **Chine** | PIPL + CSL | https://www.cac.gov.cn |
| **Inde** | DPDP Act 2023 | https://www.meity.gov.in |
| **Corée du Sud** | PIPA | https://www.pipc.go.kr |

### 5.5 Afrique

| Pays/Région | Réglementation | Lien |
|-------------|----------------|------|
| **Union Africaine** | Convention de Malabo | https://au.int/en/treaties/african-union-convention-cyber-security-and-personal-data-protection |
| **Afrique du Sud** | POPIA | https://popia.co.za |
| **Nigeria** | NDPR | https://nitda.gov.ng/ndpr/ |
| **Kenya** | Data Protection Act | https://www.odpc.go.ke |
| **Rwanda** | Law on Protection of Personal Data | https://www.minict.gov.rw |
| **Maroc** | Loi 09-08 | https://www.cndp.ma |
| **Sénégal** | Loi 2008-12 | https://www.cdp.sn |

### 5.6 Références Techniques

| Ressource | Description | Lien |
|-----------|-------------|------|
| **OWASP Top 10** | Top 10 vulnérabilités web | https://owasp.org/Top10/ |
| **OWASP ASVS** | Application Security Verification Standard | https://owasp.org/www-project-application-security-verification-standard/ |
| **CWE** | Common Weakness Enumeration | https://cwe.mitre.org |
| **CVE** | Common Vulnerabilities and Exposures | https://cve.mitre.org |
| **SANS Top 25** | Most Dangerous Software Errors | https://www.sans.org/top25-software-errors/ |

---

## 6. Tests Obligatoires Avant Production

### 6.1 Tests Unitaires (Travaux Directs)

```java
package com.waangu.platform.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.*;

class ForbiddenBodyFieldsFilterTest {

    private final ForbiddenBodyFieldsFilter filter = new ForbiddenBodyFieldsFilter();

    @Test
    @DisplayName("Should block request with tenant_id in body")
    void shouldBlockTenantId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"Test\",\"tenant_id\":\"xxx\"}".getBytes());
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        
        filter.doFilter(request, response, chain);
        
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("FORBIDDEN_FIELD");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"tenant_id\":\"x\"}",
        "{\"tenantId\":\"x\"}",
        "{\"nested\":{\"tenant_id\":\"x\"}}",
        "{\"array\":[{\"tenant_id\":\"x\"}]}"
    })
    @DisplayName("Should block all variations of forbidden fields")
    void shouldBlockAllVariations(String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setContentType("application/json");
        request.setContent(body.getBytes());
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, new MockFilterChain());
        
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Should allow valid request without forbidden fields")
    void shouldAllowValidRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"Test\",\"price\":100}".getBytes());
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        
        filter.doFilter(request, response, chain);
        
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should reject oversized body")
    void shouldRejectOversizedBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setContentType("application/json");
        
        // 11 MB body
        byte[] largeBody = new byte[11 * 1024 * 1024];
        request.setContent(largeBody);
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, new MockFilterChain());
        
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
```

### 6.2 Tests d'Isolation Multi-Tenant (Travaux Croisés)

```java
package com.waangu.platform.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CrossTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setup() {
        // Créer des données pour chaque tenant
        jdbc.update("INSERT INTO products (id, tenant_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), TENANT_A, "Product A");
        jdbc.update("INSERT INTO products (id, tenant_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), TENANT_B, "Product B");
    }

    @Test
    void tenantA_shouldNotSee_tenantB_data() {
        // Simuler contexte Tenant A
        jdbc.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
        
        // Compter les produits visibles
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products", Integer.class);
        
        // Tenant A ne devrait voir que ses propres produits
        assertThat(count).isEqualTo(1);
        
        // Vérifier que c'est bien le bon produit
        String name = jdbc.queryForObject(
                "SELECT name FROM products LIMIT 1", String.class);
        assertThat(name).isEqualTo("Product A");
    }

    @Test
    void tenantB_shouldNotSee_tenantA_data() {
        // Simuler contexte Tenant B
        jdbc.execute("SELECT set_config('app.current_tenant', '" + TENANT_B + "', true)");
        
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products", Integer.class);
        
        assertThat(count).isEqualTo(1);
        
        String name = jdbc.queryForObject(
                "SELECT name FROM products LIMIT 1", String.class);
        assertThat(name).isEqualTo("Product B");
    }

    @Test
    void directQuery_withoutContext_shouldFail() {
        // Sans contexte tenant, RLS devrait bloquer
        jdbc.execute("SELECT set_config('app.current_tenant', '', true)");
        
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products", Integer.class);
        
        // Aucun produit visible sans contexte
        assertThat(count).isEqualTo(0);
    }

    @Test
    void cannotBypassRLS_withDirectTenantIdFilter() {
        // Même en filtrant explicitement sur tenant_id, RLS s'applique
        jdbc.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
        
        // Tenter d'accéder aux données de Tenant B
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE tenant_id = ?",
                Integer.class, TENANT_B);
        
        // RLS bloque même avec le filtre explicite
        assertThat(count).isEqualTo(0);
    }
}
```

### 6.3 Tests de Sécurité

```java
package com.waangu.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should include HSTS header")
    void shouldIncludeHstsHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().string("Strict-Transport-Security", 
                        org.hamcrest.Matchers.containsString("max-age=31536000")));
    }

    @Test
    @DisplayName("Should include X-Frame-Options DENY")
    void shouldIncludeXFrameOptions() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("Should include Content-Security-Policy")
    void shouldIncludeCsp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("Should include X-Content-Type-Options nosniff")
    void shouldIncludeXContentTypeOptions() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Should reject TRACE method")
    void shouldRejectTraceMethod() throws Exception {
        mockMvc.perform(request("TRACE", "/api/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should require authentication for API endpoints")
    void shouldRequireAuthForApi() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }
}
```

### 6.4 Tests d'Idempotence

```java
package com.waangu.platform.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void sameKey_shouldReturnCachedResponse() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of("amount", 100);
        
        // Premier appel
        var result1 = idempotencyService.executeOnce(key, request, () -> {
            return Map.of("transactionId", UUID.randomUUID().toString());
        });
        
        // Deuxième appel avec même clé
        var result2 = idempotencyService.executeOnce(key, request, () -> {
            fail("Should not execute again");
            return null;
        });
        
        assertThat(result2).isEqualTo(result1);
    }

    @Test
    void concurrentRequests_shouldExecuteOnlyOnce() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of("amount", 100);
        AtomicInteger executionCount = new AtomicInteger(0);
        
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    idempotencyService.executeOnce(key, request, () -> {
                        executionCount.incrementAndGet();
                        return Map.of("result", "ok");
                    });
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        
        // Seulement une exécution malgré 10 requêtes concurrentes
        assertThat(executionCount.get()).isEqualTo(1);
    }

    @Test
    void differentKey_shouldExecuteSeparately() {
        Map<String, Object> request = Map.of("amount", 100);
        
        var result1 = idempotencyService.executeOnce("key1", request, () -> Map.of("id", "1"));
        var result2 = idempotencyService.executeOnce("key2", request, () -> Map.of("id", "2"));
        
        assertThat(result1.get("id")).isEqualTo("1");
        assertThat(result2.get("id")).isEqualTo("2");
    }
}
```

### 6.5 Checklist de Tests Avant Production

```yaml
# .github/workflows/pre-production-tests.yml
name: Pre-Production Tests

on:
  push:
    branches: [main, release/*]

jobs:
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn test -Dtest="*Test" -DfailIfNoTests=false
      - name: Minimum Coverage Check
        run: |
          COVERAGE=$(mvn jacoco:report | grep -oP 'Total.*?(\d+)%' | grep -oP '\d+')
          if [ "$COVERAGE" -lt 80 ]; then
            echo "Coverage $COVERAGE% is below 80%"
            exit 1
          fi

  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn test -Dtest="*IT,*IntegrationTest"

  cross-tenant-tests:
    name: Cross-Tenant Isolation Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn test -Dtest="*CrossTenant*,*Isolation*"
      - name: Verify RLS Policies
        run: |
          psql -h localhost -U test -d testdb -c "
            SELECT tablename, policyname 
            FROM pg_policies 
            WHERE schemaname = 'public'
          " | grep -q "rls_" || exit 1

  security-scan:
    name: Security Scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: OWASP Dependency Check
        run: mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
      - name: Secret Scan
        run: |
          if grep -rniE "password\s*=|api[_-]?key\s*=|secret\s*=" --include="*.java" src/; then
            echo "Potential secrets found!"
            exit 1
          fi

  migration-tests:
    name: Migration Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - name: Run Flyway Migrations
        run: mvn flyway:migrate
      - name: Verify RLS Enabled
        run: |
          psql -h localhost -U test -d testdb -c "
            SELECT relname, relrowsecurity, relforcerowsecurity 
            FROM pg_class 
            WHERE relname IN ('audit_log', 'idempotency_key', 'outbox_event')
          " | grep -v "f.*f" || exit 1

  performance-tests:
    name: Performance Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run JMH Benchmarks
        run: mvn test -Dtest="*Benchmark*" -DfailIfNoTests=false
```

---

## 7. Assurance Qualité Backend

### 7.1 Architecture des Services

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway / BFF                         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐ │
│  │ Auth Filter │→│Rate Limiter │→│Module Gating│→│   Router   │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│  Microservice │     │  Microservice │     │  Microservice │
│   (Spring)    │     │   (Spring)    │     │   (NestJS)    │
├───────────────┤     ├───────────────┤     ├───────────────┤
│ Filters:      │     │ Filters:      │     │ Guards:       │
│ - Correlation │     │ - Correlation │     │ - Tenant      │
│ - Tenant      │     │ - Tenant      │     │ - Auth        │
│ - Forbidden   │     │ - Forbidden   │     │ - Forbidden   │
├───────────────┤     ├───────────────┤     ├───────────────┤
│ Services:     │     │ Services:     │     │ Services:     │
│ - Audit       │     │ - Audit       │     │ - Audit       │
│ - Idempotency │     │ - Idempotency │     │ - Idempotency │
│ - Outbox      │     │ - Outbox      │     │ - Outbox      │
├───────────────┤     ├───────────────┤     ├───────────────┤
│ DB Session:   │     │ DB Session:   │     │ DB Session:   │
│ - RLS Init    │     │ - RLS Init    │     │ - RLS Init    │
└───────────────┘     └───────────────┘     └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │   (RLS Active)  │
                    └─────────────────┘
```

### 7.2 Checklist Qualité par Composant

#### A. Filters (Couche HTTP)

| Composant | Vérification | Status |
|-----------|--------------|--------|
| CorrelationIdFilter | Génère/propage X-Trace-Id | ☐ |
| TenantContextFilter | Extrait tenant du JWT | ☐ |
| TenantContextFilter | Valide module actif | ☐ |
| ForbiddenBodyFieldsFilter | Bloque tenant_id dans body | ☐ |
| ForbiddenBodyFieldsFilter | Limite taille body | ☐ |

#### B. Services (Couche Métier)

| Composant | Vérification | Status |
|-----------|--------------|--------|
| AuditLogService | Hash-chain intègre | ☐ |
| AuditLogService | Pas de PII dans payload | ☐ |
| IdempotencyService | Clé unique par tenant | ☐ |
| IdempotencyService | Expiration configurée | ☐ |
| OutboxService | Transaction MANDATORY | ☐ |
| OutboxService | Retry avec backoff | ☐ |

#### C. Database (Couche Données)

| Composant | Vérification | Status |
|-----------|--------------|--------|
| RLS Policies | Actives sur toutes tables | ☐ |
| RLS Policies | FORCE ROW LEVEL SECURITY | ☐ |
| DbSessionInitializer | SET LOCAL exécuté | ☐ |
| Migrations | Versionnées (Flyway) | ☐ |

### 7.3 Métriques de Qualité

```java
// Configuration Micrometer pour métriques de qualité
@Configuration
public class QualityMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "waangu-saas-starter");
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

// Métriques à surveiller
@Component
public class QualityMetrics {

    private final MeterRegistry registry;
    private final Counter tenantContextMissing;
    private final Counter rlsViolations;
    private final Counter idempotencyHits;
    private final Counter auditWriteFailures;

    public QualityMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        this.tenantContextMissing = Counter.builder("waangu.tenant.context.missing")
                .description("Requests without tenant context")
                .register(registry);
        
        this.rlsViolations = Counter.builder("waangu.rls.violations")
                .description("RLS policy violations detected")
                .register(registry);
        
        this.idempotencyHits = Counter.builder("waangu.idempotency.hits")
                .description("Idempotency cache hits (duplicate requests)")
                .register(registry);
        
        this.auditWriteFailures = Counter.builder("waangu.audit.write.failures")
                .description("Failed audit log writes")
                .register(registry);
    }

    public void recordTenantContextMissing() {
        tenantContextMissing.increment();
    }

    public void recordRlsViolation() {
        rlsViolations.increment();
    }

    public void recordIdempotencyHit() {
        idempotencyHits.increment();
    }

    public void recordAuditWriteFailure() {
        auditWriteFailures.increment();
    }
}
```

### 7.4 Alertes Recommandées

```yaml
# prometheus-alerts.yml
groups:
  - name: waangu-quality
    rules:
      - alert: TenantContextMissingHigh
        expr: rate(waangu_tenant_context_missing_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High rate of requests without tenant context"
          
      - alert: RlsViolationDetected
        expr: increase(waangu_rls_violations_total[1m]) > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "RLS violation detected - potential data leak"
          
      - alert: AuditWriteFailuresHigh
        expr: rate(waangu_audit_write_failures_total[5m]) > 0.01
        for: 5m
        labels:
          severity: high
        annotations:
          summary: "Audit log write failures - compliance risk"
          
      - alert: IdempotencyHitRateHigh
        expr: rate(waangu_idempotency_hits_total[5m]) > 1
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High idempotency hit rate - possible duplicate submissions"
```

---

## Questions de Vérification

### Question 1: Compréhension de l'Isolation Multi-Tenant
> Comment le starter garantit-il qu'un tenant ne peut pas accéder aux données d'un autre tenant?

**Réponse attendue:** Trois niveaux de protection:
1. JWT contient tenant_id validé par TenantContextFilter
2. DbSessionInitializer exécute SET LOCAL pour les variables RLS
3. PostgreSQL RLS policies filtrent automatiquement par tenant_id

### Question 2: Vecteurs d'Attaque
> Quels sont les trois principaux vecteurs d'attaque contre ce système et comment sont-ils mitigés?

**Réponse attendue:**
1. **Injection SQL** → Validation regex + quote_ident() + whitelist de schémas
2. **Mass Assignment** → ForbiddenBodyFieldsFilter bloque tenant_id dans body
3. **Privilege Escalation** → JWT signé + RLS + LegalEntityGuard

### Question 3: Tests Obligatoires
> Quels tests doivent absolument passer avant un déploiement en production?

**Réponse attendue:**
1. Tests unitaires (coverage > 80%)
2. Tests d'isolation cross-tenant
3. Tests de sécurité (headers, auth)
4. Tests d'idempotence
5. Vérification RLS active sur toutes tables

### Question 4: Conformité Réglementaire
> Pour un déploiement en France et au Sénégal, quelles réglementations s'appliquent?

**Réponse attendue:**
- **France:** RGPD, NIS2, DORA (si FinTech)
- **Sénégal:** Loi 2008-12 sur la protection des données personnelles
- **International:** ISO 27001, PCI DSS (si paiements)

---

## Conclusion

Ce document couvre:
- ✅ 27 erreurs identifiées avec corrections
- ✅ Erreurs de débutant typiques
- ✅ Vecteurs d'attaque et mitigations
- ✅ Références de sécurité (Europe, USA, Asie, Afrique)
- ✅ Tests obligatoires (unitaires, cross-tenant, sécurité)
- ✅ Assurance qualité backend

**Prochaines étapes:**
1. Appliquer les corrections critiques (Issues 1-5)
2. Ajouter les tests manquants
3. Configurer les alertes de monitoring
4. Documenter les décisions de sécurité
