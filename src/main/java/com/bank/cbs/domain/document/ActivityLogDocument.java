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

@Document(collection = "activity_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogDocument {
    @Id
    private String id;

    @Indexed
    @Field("customer_id")
    private UUID customerId;

    @Field("activity_type")
    private String activityType;    // LOGIN, LOGOUT, VIEW_BALANCE, DOWNLOAD_STATEMENT

    @Field("channel")
    private String channel;

    @Field("device_id")
    private String deviceId;

    @Field("ip_address")
    private String ipAddress;

    @Field("user_agent")
    private String userAgent;

    @Field("session_id")
    private String sessionId;

    @Field("request_uri")
    private String requestUri;

    @Field("http_method")
    private String httpMethod;

    @Field("response_code")
    private Integer responseCode;

    @Field("duration_ms")
    private Long durationMs;

    @Field("metadata")
    private Map<String, Object> metadata;

    @CreatedDate
    @Indexed
    @Field("created_at")
    private Instant createdAt;
}
