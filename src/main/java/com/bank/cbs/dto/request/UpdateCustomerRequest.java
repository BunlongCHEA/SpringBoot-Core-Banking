package com.bank.cbs.dto.request;

import java.time.LocalDate;

public record UpdateCustomerRequest(
    String fullName,
    String phone,
    LocalDate dateOfBirth
) {
    
}
