package com.bank.cbs.repository.mongo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.document.NotificationDocument;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {
    Page<NotificationDocument> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    List<NotificationDocument> findByStatusAndRetryCountLessThan(String status, int maxRetry);
}
