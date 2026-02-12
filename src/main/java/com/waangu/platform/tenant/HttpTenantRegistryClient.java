package com.waangu.platform.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "waangu.tenant-registry-url")
public class HttpTenantRegistryClient implements TenantRegistryClient {

    private final RestClient restClient;

    public HttpTenantRegistryClient(
            RestClient.Builder builder,
            @Value("${waangu.tenant-registry-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public TenantDbResolution resolve(String tenantId) {
        return restClient.get()
                .uri("/tenants/{id}/db-resolution", tenantId)
                .retrieve()
                .body(TenantDbResolution.class);
    }
}
