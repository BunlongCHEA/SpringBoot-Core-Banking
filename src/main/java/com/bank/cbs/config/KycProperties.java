package com.bank.cbs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kyc")
public class KycProperties {
    private String baseUrl;
    private String apiKey;
    private Mq mq = new Mq();

    @Getter
    @Setter
    public static class Mq {
        // JSON map of version → base64 32-byte key, e.g. {"v1":"...","v2":"..."}
        // Populated from a secrets manager / vault in production — env var here for simplicity.
        private String keysJson;
        private String requestSigningSecret; // unrelated to MQ — keep for API request signing
    }
}
