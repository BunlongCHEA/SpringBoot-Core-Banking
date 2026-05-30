package com.bank.cbs.config;


import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.bank.cbs.repository.mongo")
public class MongoConfig {
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:core_banking_logs}")
    private String mongoDatabase;

    @Bean
    public MongoClient mongoClient() {
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoUri))
            .applyToSocketSettings(builder ->
                builder.connectTimeout(10, TimeUnit.SECONDS)
                       .readTimeout(30, TimeUnit.SECONDS)
            )
            .applyToClusterSettings(builder ->
                builder.serverSelectionTimeout(30, TimeUnit.SECONDS)
            )
            .build();
        return MongoClients.create(settings);
    }

    @Bean("mongoTransactionManager")
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
