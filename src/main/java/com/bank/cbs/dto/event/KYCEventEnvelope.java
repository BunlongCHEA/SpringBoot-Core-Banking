package com.bank.cbs.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KYCEventEnvelope(
    @JsonProperty("message_id")  String messageId,
    @JsonProperty("timestamp")   long   timestamp,
    @JsonProperty("event_type")  String eventType,
    @JsonProperty("key_version") String keyVersion,
    @JsonProperty("ciphertext")  String ciphertext
) {
    
}
