package com.bank.cbs.repository.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.ProductConfigDocument;

@Repository
public interface ProductConfigRepository extends MongoRepository<ProductConfigDocument, String> {
    Optional<ProductConfigDocument> findByConfigKeyAndIsActiveTrue(String configKey);

    List<ProductConfigDocument> findByProductTypeAndIsActiveTrue(String productType);

    boolean existsByConfigKey(String configKey);
}
