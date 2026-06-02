package com.bank.cbs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GoKycClientConfig {
    @Value("${go-kyc.base-url}")
    private String baseUrl;

    @Value("${go-kyc.api-key}")
    private String apiKey;

    @Bean("goKycRestClient")
    public RestClient goKycRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
