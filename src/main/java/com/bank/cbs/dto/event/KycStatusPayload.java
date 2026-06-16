package com.bank.cbs.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KycStatusPayload(
    @JsonProperty("customer_id") String customerId,
    @JsonProperty("kyc_status")  String kycStatus,
    @JsonProperty("bank_id")     String bankId,
    @JsonProperty("actor")       String actor,
    @JsonProperty("changed_at")  long   changedAt
) {
    
}
