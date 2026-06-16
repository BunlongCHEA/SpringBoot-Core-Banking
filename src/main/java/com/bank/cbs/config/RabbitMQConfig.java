package com.bank.cbs.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {
    public static final String QUEUE         = "cbs.kyc.status-changed";
    public static final String EXCHANGE      = "kyc.events";
    public static final String ROUTING_KEY   = "kyc.status.changed";
    public static final String DLQ           = "cbs.kyc.status-changed.dlq";
    public static final String DL_EXCHANGE   = "kyc.events.dlx";

    private final RabbitMQProperties rabbitProps;

    // ── Connection factory

    @Bean
    public ConnectionFactory connectionFactory() {
        com.rabbitmq.client.ConnectionFactory cf = new com.rabbitmq.client.ConnectionFactory();
        cf.setHost(rabbitProps.getHost());
        cf.setPort(rabbitProps.getPort());
        cf.setUsername(rabbitProps.getUsername());
        cf.setPassword(rabbitProps.getPassword());
        cf.setVirtualHost(rabbitProps.getVirtualHost());

        if (rabbitProps.getSsl().isEnabled()) {
            try {
                cf.useSslProtocol();
                cf.enableHostnameVerification(); // maps to verify-hostname: true
                log.info("[RabbitMQ] TLS enabled, hostname verification: {}",
                        rabbitProps.getSsl().isVerifyHostname());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure RabbitMQ TLS", e);
            }
        }

        CachingConnectionFactory factory = new CachingConnectionFactory(cf);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        return factory;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ── Queue / exchange / binding declarations

    @Bean
    public Queue kycStatusQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DL_EXCHANGE)
                .withArgument("x-message-ttl", 86_400_000) // 24 h
                .build();
    }

    @Bean
    public TopicExchange kycExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding kycBinding(Queue kycStatusQueue, TopicExchange kycExchange) {
        return BindingBuilder.bind(kycStatusQueue).to(kycExchange).with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlExchange() {
        return new DirectExchange(DL_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dlBinding(Queue dlq, DirectExchange dlExchange) {
        return BindingBuilder.bind(dlq).to(dlExchange).with(QUEUE);
    }
}
