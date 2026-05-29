package com.bank.cbs.repository.mongo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.CustomerPreferenceDocument;

@Repository
public interface CustomerPreferenceRepository extends MongoRepository<CustomerPreferenceDocument, String> {
    Optional<CustomerPreferenceDocument> findByCustomerId(UUID customerId);

    boolean existsByCustomerId(UUID customerId);
}
