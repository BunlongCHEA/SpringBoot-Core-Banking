package com.bank.cbs.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.bank.cbs.domain.document.NotificationDocument;
import com.bank.cbs.repository.mongo.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Async
    public void send(UUID customerId, String channel, String type,
                     String title, String body, Map<String, Object> payload) {
        NotificationDocument doc = NotificationDocument.builder()
            .customerId(customerId)
            .channel(channel)
            .type(type)
            .title(title)
            .body(body)
            .payload(payload)
            .status("PENDING")
            .retryCount(0)
            .build();

        NotificationDocument saved = notificationRepository.save(doc);
        log.info("Notification queued: {} channel={} type={}", saved.getId(), channel, type);
        // In production: dispatch to SMS/email/push provider here
        saved.setStatus("SENT");
        notificationRepository.save(saved);
    }

    public Page<NotificationDocument> findByCustomer(UUID customerId, Pageable pageable) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }
}
