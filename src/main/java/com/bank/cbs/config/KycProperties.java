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
        private String aesKey;
        private String hmacKey;
        private String requestSigningSecret;
    }
}
