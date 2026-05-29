package com.bank.cbs.repository.mongo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.AuditEventDocument;

@Repository
public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {
    Page<AuditEventDocument> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
        String entityType, String entityId, Pageable pageable
    );

    Page<AuditEventDocument> findByPerformedByOrderByOccurredAtDesc(
        String performedBy, Pageable pageable
    );

    List<AuditEventDocument> findByEntityTypeAndActionAndOccurredAtBetween(
        String entityType, String action, Instant from, Instant to
    );
}
