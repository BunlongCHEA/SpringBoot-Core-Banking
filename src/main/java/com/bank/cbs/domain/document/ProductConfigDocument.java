package com.bank.cbs.domain.document;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "product_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductConfigDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("config_key")
    private String configKey;           // e.g., "SAVINGS_INTEREST_RATE", "TRANSFER_FEE_RULES"

    @Field("product_type")
    private String productType;         // SAVINGS, CHECKING, LOAN, CARD

    @Field("version")
    private Integer version = 1;

    @Field("is_active")
    private boolean isActive = true;

    @Field("rules")
    private Map<String, Object> rules;  // Flexible JSON rules

    @Field("limits")
    private Map<String, Object> limits;

    @Field("fees")
    private Map<String, Object> fees;

    @Field("eligibility")
    private Map<String, Object> eligibility;

    @Field("description")
    private String description;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
