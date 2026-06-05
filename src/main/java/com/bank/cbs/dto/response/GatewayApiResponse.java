package com.bank.cbs.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayApiResponse<T>(
 
        boolean success,
        String  message,
 
        /** The actual payload — matches the Go-KYC response body. */
        T data,
 
        /** Present when Go-KYC returns an error at the application level. */
        String error
) {
    
}
