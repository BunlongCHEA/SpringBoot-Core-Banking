package com.bank.cbs.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the KYCData model returned by Go-Blockchain-KYC's external verify endpoint.
 * Field names match Go's JSON snake_case tags.
 */
public record GoKycVerifyResponse(

        @JsonProperty("customer_id")    String customerId,
        @JsonProperty("first_name")     String firstName,
        @JsonProperty("last_name")      String lastName,
        @JsonProperty("date_of_birth")  String dateOfBirth,
        @JsonProperty("nationality")    String nationality,
        @JsonProperty("id_type")        String idType,
        @JsonProperty("id_number")      String idNumber,
        @JsonProperty("id_expiry_date") String idExpiryDate,
        @JsonProperty("address")        GoKycAddressDto address,
        @JsonProperty("email")          String email,
        @JsonProperty("phone")          String phone,
        @JsonProperty("status")         String status,
        @JsonProperty("bank_id")        String bankId,

        // Fields from the Go_KYC `users` table joined in the response
        @JsonProperty("user_role")      String userRole,
        @JsonProperty("is_active")      Boolean isActive,
        @JsonProperty("is_deleted")     Boolean isDeleted
) {
    public record GoKycAddressDto(
            @JsonProperty("street")      String street,
            @JsonProperty("city")        String city,
            @JsonProperty("state")       String state,
            @JsonProperty("postal_code") String postalCode,
            @JsonProperty("country")     String country
    ) {}
}
