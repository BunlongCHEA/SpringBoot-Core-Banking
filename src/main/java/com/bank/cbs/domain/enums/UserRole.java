package com.bank.cbs.domain.enums;

public enum UserRole {
    SUPER_ADMIN,        // Full access — operates everything
    ADMIN,              // Bank admin — manages users, branches, configuration
    CUSTOMER_SERVICE,   // Creates/manages customer records, requests accounts on behalf of customers
    TELLER,             // Processes transactions, account operations
    AUDITOR,            // Read-only access to audit logs and reports
    CUSTOMER            // Reserved for future mobile-banking scope (not this project)
}
