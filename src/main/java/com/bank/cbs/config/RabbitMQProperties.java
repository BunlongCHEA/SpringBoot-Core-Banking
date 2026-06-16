package com.bank.cbs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMQProperties {
    private String  host        = "rabbitmq";
    private int     port        = 5671;
    private String  username    = "cbs_consumer";
    private String  password;
    private String  virtualHost = "kyc_vhost";
    private Ssl     ssl         = new Ssl();

    @Getter
    @Setter
    public static class Ssl {
        private boolean enabled         = true;
        private boolean verifyHostname  = true;
    }
}
