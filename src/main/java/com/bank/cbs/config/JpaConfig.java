package com.bank.cbs.config;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableJpaRepositories(basePackages = "com.bank.cbs.repository.jpa")
public class JpaConfig {
    
    /**
     * Provides OffsetDateTime for @CreatedDate / @LastModifiedDate in BaseEntity.
     * Without this, Spring defaults to LocalDateTime which cannot be assigned
     * to OffsetDateTime fields, causing InvalidDataAccessApiUsageException.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }

    /**
     * Declare JPA transaction manager as @Primary so that plain @Transactional
     * always binds to PostgreSQL, not to the MongoTransactionManager.
     */
    @Primary
    @Bean("transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
