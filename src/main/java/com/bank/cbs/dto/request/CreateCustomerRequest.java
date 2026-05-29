package com.bank.cbs.dto.request;

import java.time.LocalDate;

import com.bank.cbs.domain.enums.CustomerType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateCustomerRequest(
    @NotBlank  String fullName,
    @NotBlank  @Email String email,
    @NotBlank  @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phone,
    String nationalId,
    LocalDate dateOfBirth,
    @NotNull   CustomerType customerType
) {
    
}
