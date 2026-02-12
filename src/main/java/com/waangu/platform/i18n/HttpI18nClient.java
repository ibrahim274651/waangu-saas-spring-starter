package com.waangu.platform.i18n;

import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "waangu.translation-url")
public class HttpI18nClient implements I18nClient {

    private final RestClient restClient;

    public HttpI18nClient(
            RestClient.Builder builder,
            @Value("${waangu.translation-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void upsertSource(String key, String locale, String text) {
        var ctx = TenantContextHolder.get();
        String tenantId = ctx != null ? ctx.tenantId() : "";
        restClient.post()
                .uri("/i18n/keys")
                .header("X-Tenant-Id", tenantId)
                .body(Map.of("key", key, "locale", locale, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public Optional<String> translate(String key, String locale) {
        try {
            var ctx = TenantContextHolder.get();
            String tenantId = ctx != null ? ctx.tenantId() : "";
            String out = restClient.get()
                    .uri("/i18n/keys/{key}?locale={locale}", key, locale)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(out);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
