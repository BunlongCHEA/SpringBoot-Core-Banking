package com.bank.cbs.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GoKycClientConfig {
    @Value("${kyc.base-url}")
    private String baseUrl;

    @Value("${kyc.api-key}")
    private String apiKey;

    // @Bean("goKycRestClient")
    // public RestClient goKycRestClient() {
    //     return RestClient.builder()
    //             .baseUrl(baseUrl)
    //             .defaultHeader("Authorization", "Bearer " + apiKey) // NextJS integration key
    //             .defaultHeader("Content-Type", "application/json")
    //             .build();
    // }

    @Bean("goKycRestClient")
    public RestClient goKycRestClient() {
 
        // ── Connection pool ───────────────────────────────────────────────
        PoolingHttpClientConnectionManager connManager =
                new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(20);
        connManager.setDefaultMaxPerRoute(10);
 
        // ── Timeouts ──────────────────────────────────────────────────────
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();
 
        // ── Build Apache HttpClient 5 ─────────────────────────────────────
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                // Retry once on stale-connection / reset errors (safe for POST
                // when the request was not yet sent to the server)
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .build();
 
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
 
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
