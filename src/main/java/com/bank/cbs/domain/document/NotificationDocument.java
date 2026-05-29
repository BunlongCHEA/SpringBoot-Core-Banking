package com.bank.cbs.domain.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {
    @Id
    private String id;

    @Indexed
    @Field("customer_id")
    private UUID customerId;

    @Field("channel")
    private String channel;           // SMS, EMAIL, PUSH, IN_APP

    @Field("type")
    private String type;              // TRANSACTION_ALERT, OTP, WELCOME, etc.

    @Field("title")
    private String title;

    @Field("body")
    private String body;

    @Field("payload")
    private Map<String, Object> payload;

    @Field("status")
    private String status;            // SENT, DELIVERED, FAILED, PENDING

    @Indexed
    @Field("reference_id")
    private String referenceId;

    @Field("retry_count")
    private int retryCount = 0;

    @Field("error_message")
    private String errorMessage;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @Field("delivered_at")
    private Instant deliveredAt;
}
