package com.bank.cbs.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registers a shared, primary ObjectMapper bean.
 *
 * WHY THIS IS NEEDED
 * ------------------
 * Spring Boot's JacksonAutoConfiguration would normally provide this bean, but
 * custom RabbitMQ beans in RabbitMQConfig (connectionFactory, rabbitTemplate)
 * suppress several AMQP auto-configurations, which disrupts the Jackson
 * auto-config chain in Spring Boot 4.x.
 *
 * Additionally, RedisConfig creates ObjectMapper instances with
 *   new ObjectMapper()
 * inside a method body — those are local variables, NOT @Beans, so Spring
 * never registers them and cannot inject them into KycEventDecryptor.
 *
 * This class fixes both problems by providing the one canonical ObjectMapper
 * that the whole application shares.
 *
 * REDIS NOTE
 * ----------
 * RedisConfig deliberately uses its *own* locally-created ObjectMapper with
 *   activateDefaultTyping(NON_FINAL)
 * enabled. That setting must NOT be applied to the shared mapper — it would
 * cause issues in REST responses and general JSON handling. The Redis mapper
 * stays as a local variable inside redisTemplate(); that is correct and
 * intentional.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Handle Java 8+ date/time types (Instant, LocalDate, ZonedDateTime …)
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings, not epoch milliseconds
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

        // Don't blow up on unknown JSON fields (forward-compatible)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}