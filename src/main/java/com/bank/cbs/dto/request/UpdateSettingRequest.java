package com.bank.cbs.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateSettingRequest(
    @NotBlank String value
) {}
