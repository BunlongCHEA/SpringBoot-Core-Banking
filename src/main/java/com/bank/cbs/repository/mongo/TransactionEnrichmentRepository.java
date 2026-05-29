package com.bank.cbs.repository.mongo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.TransactionEnrichmentDocument;

@Repository
public interface TransactionEnrichmentRepository extends MongoRepository<TransactionEnrichmentDocument, String> {

    Optional<TransactionEnrichmentDocument> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);
}