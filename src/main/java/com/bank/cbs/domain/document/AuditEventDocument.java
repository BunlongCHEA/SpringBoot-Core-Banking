package com.bank.cbs.domain.document;

import java.time.Instant;
import java.util.Map;

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

@Document(collection = "audit_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDocument {
    @Id
    private String id;

    @Indexed
    @Field("entity_type")
    private String entityType;

    @Indexed
    @Field("entity_id")
    private String entityId;

    @Field("action")
    private String action;

    @Indexed
    @Field("performed_by")
    private String performedBy;

    @Field("role")
    private String role;

    @Field("ip_address")
    private String ipAddress;

    @Field("user_agent")
    private String userAgent;

    @Field("session_id")
    private String sessionId;

    @Field("before_state")
    private Map<String, Object> beforeState;

    @Field("after_state")
    private Map<String, Object> afterState;

    @Field("diff")
    private Map<String, Object> diff;

    @Field("context")
    private Map<String, Object> context;

    @CreatedDate
    @Indexed
    @Field("occurred_at")
    private Instant occurredAt;
}
