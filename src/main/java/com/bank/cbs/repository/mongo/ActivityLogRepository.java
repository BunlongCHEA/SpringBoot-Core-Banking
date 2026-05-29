package com.bank.cbs.repository.mongo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.ActivityLogDocument;

@Repository
public interface ActivityLogRepository extends MongoRepository<ActivityLogDocument, String> {
    Page<ActivityLogDocument> findByCustomerIdOrderByCreatedAtDesc(
        UUID customerId, Pageable pageable
    );

    List<ActivityLogDocument> findByCustomerIdAndActivityTypeAndCreatedAtBetween(
        UUID customerId, String activityType, Instant from, Instant to
    );

    List<ActivityLogDocument> findBySessionId(String sessionId);
}
