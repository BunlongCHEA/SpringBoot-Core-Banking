package com.bank.cbs;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts throwaway Docker containers for every service the CBS needs,
 * so `./mvnw verify` works on any machine with Docker — including Jenkins.
 *
 * Spring Boot starts each container before the application context,
 * and @ServiceConnection overrides spring.datasource / redis / mongodb
 * connection settings automatically.
 *
 * Keep image versions aligned with what UAT/Production actually run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as the real database (PostgreSQL 15). */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:7"));
    }

    /**
     * RabbitMQ uses the project's own `rabbitmq.*` properties (RabbitMQProperties),
     * not spring.rabbitmq.*, so @ServiceConnection can't wire it.
     * We register the container's host/port/credentials manually instead.
     */
    @Bean
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));
    }

    @Bean
    DynamicPropertyRegistrar rabbitProperties(RabbitMQContainer rabbit) {
        return registry -> {
            registry.add("rabbitmq.host", rabbit::getHost);
            registry.add("rabbitmq.port", rabbit::getAmqpPort);
            registry.add("rabbitmq.username", rabbit::getAdminUsername);
            registry.add("rabbitmq.password", rabbit::getAdminPassword);
            registry.add("rabbitmq.virtual-host", () -> "/");
            registry.add("rabbitmq.ssl.enabled", () -> "false");
        };
    }
}
