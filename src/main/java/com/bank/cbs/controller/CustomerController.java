package com.bank.cbs.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.dto.request.CreateCustomerFromKycRequest;
import com.bank.cbs.dto.request.CreateCustomerRequest;
import com.bank.cbs.dto.request.UpdateCustomerRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.CustomerResponse;
import com.bank.cbs.dto.response.PageResponse;
import com.bank.cbs.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@EnableMethodSecurity
@Tag(name = "Customers", description = "Customer management APIs, including creation, retrieval, updating, and status management.")
public class CustomerController {
    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')") // deliberately narrower than createFromKyc
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Customer created", customerService.create(request)));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> findById(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.findById(customerId)));
    }

    @GetMapping
    @Operation(summary = "Search customers")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> search(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = customerService.search(
            status, search,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Customer updated",
            customerService.update(customerId, request)));
    }

    @PatchMapping("/{customerId}/status")
    @Operation(summary = "Update customer status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable UUID customerId,
            @RequestParam CustomerStatus status) {
        customerService.updateStatus(customerId, status);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", null));
    }

    /**
     * Creates a customer record only after verifying their identity against
     * the Go-Blockchain-KYC system.
     *
     * Required roles: CUSTOMER_SERVICE, ADMIN, SUPER_ADMIN.
     */
    @PostMapping("/kyc-verified")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN','CUSTOMER_SERVICE')")
    @Operation(summary = "Create customer from verified KYC record (first time only)")
    public ResponseEntity<CustomerResponse> createFromKyc(
            @Valid @RequestBody CreateCustomerFromKycRequest request) {
        CustomerResponse response = customerService.createFromKyc(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PATCH /api/v1/customers/{customerId}/address/kyc
     *
     * Re-verifies the customer against Go-KYC and syncs the address stored
     * in CBS. Call this on every subsequent KYC submission after the customer
     * already exists in CBS.
     *
     * <h3>Same address (2nd, 3rd … KYC with no move)</h3>
     * <pre>
     *   addresses            │ customer_addresses
     *   ─────────────────────┼──────────────────────
     *   addr-1 primary=true  │ cust-1 | addr-1
     *                        │                        ← no rows written at all
     * </pre>
     *
     * <h3>Address changed</h3>
     * <pre>
     *   BEFORE               │ AFTER
     *   ─────────────────────┼──────────────────────────────────────────────
     *   addr-1 primary=true  │ addr-1 primary=false  ← UPDATE (demoted)
     *                        │ addr-2 primary=true   ← INSERT (new)
     *   cust-1 | addr-1      │ cust-1 | addr-1
     *                        │ cust-1 | addr-2       ← INSERT (new join row)
     * </pre>
     *
     * <h3>Moved back to a previous address (no duplicate INSERT)</h3>
     * <pre>
     *   BEFORE               │ AFTER
     *   ─────────────────────┼──────────────────────────────────────────────
     *   addr-1 primary=false │ addr-1 primary=true   ← UPDATE (re-promoted)
     *   addr-2 primary=true  │ addr-2 primary=false  ← UPDATE (demoted)
     *   cust-1 | addr-1      │ customer_addresses unchanged
     *   cust-1 | addr-2      │
     * </pre>
     *
     * Roles: SUPER_ADMIN, ADMIN, CUSTOMER_SERVICE
     */
    @PatchMapping("/{customerId}/address/kyc")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN','CUSTOMER_SERVICE')")
    @Operation(
        summary     = "Sync customer address from latest KYC verification",
        description = "Idempotent. No writes when address is unchanged. "
                    + "Demotes old primary and promotes/inserts new when address changes."
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> syncAddressFromKyc(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateCustomerFromKycRequest request) {
        CustomerResponse response = customerService.syncAddressFromKyc(customerId, request);
        return ResponseEntity.ok(ApiResponse.ok("Address synced from KYC", response));
    }
}
