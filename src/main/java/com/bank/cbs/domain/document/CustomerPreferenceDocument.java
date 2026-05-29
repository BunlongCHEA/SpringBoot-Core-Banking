package com.bank.cbs.domain.document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

@Document(collection = "customer_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPreferenceDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("customer_id")
    private UUID customerId;

    @Field("language")
    private String language = "en";

    @Field("currency")
    private String preferredCurrency = "USD";

    @Field("timezone")
    private String timezone = "UTC";

    @Field("notification_channels")
    private List<String> notificationChannels;  // EMAIL, SMS, PUSH

    @Field("alert_settings")
    private Map<String, Object> alertSettings;   // threshold, types

    @Field("security_settings")
    private Map<String, Object> securitySettings; // 2FA, biometric

    @Field("dashboard_layout")
    private Map<String, Object> dashboardLayout;  // Widget preferences

    @Field("marketing_consent")
    private boolean marketingConsent = false;

    @Field("data_sharing_consent")
    private boolean dataSharingConsent = false;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
