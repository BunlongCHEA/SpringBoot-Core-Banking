package com.bank.cbs.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.domain.enums.CustomerType;

public record CustomerResponse(
    UUID           customerId,
    String         customerCode,
    String         fullName,
    String         email,
    String         phone,
    String         nationalId,
    LocalDate      dateOfBirth,
    CustomerStatus status,
    CustomerType   customerType,
    String         bankId, 
    String         idType,
    OffsetDateTime createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
            c.getCustomerId(), c.getCustomerCode(), c.getFullName(),
            c.getEmail(), c.getPhone(), c.getNationalId(),
            c.getDateOfBirth(), c.getStatus(), c.getCustomerType(), c.getBankId(), c.getIdType(), c.getCreatedAt()
        );
    }
}
