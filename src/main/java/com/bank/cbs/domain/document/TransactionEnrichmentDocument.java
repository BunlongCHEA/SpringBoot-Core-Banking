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

@Document(collection = "transaction_enrichments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEnrichmentDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("transaction_id")
    private UUID transactionId;

    @Field("merchant_name")
    private String merchantName;

    @Field("merchant_category")
    private String merchantCategory;

    @Field("merchant_location")
    private Map<String, String> merchantLocation;

    @Field("geo_data")
    private Map<String, Double> geoData;        // lat, lng

    @Field("device_info")
    private Map<String, String> deviceInfo;     // ip, device_id, os

    @Field("fraud_signals")
    private Map<String, Object> fraudSignals;

    @Field("risk_score")
    private Double riskScore;

    @Field("tags")
    private java.util.List<String> tags;

    @Field("extra_metadata")
    private Map<String, Object> extraMetadata;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
