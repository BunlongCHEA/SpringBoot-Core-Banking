package com.bank.cbs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.bank.cbs.repository.jpa")
public class JpaConfig {
    
}
