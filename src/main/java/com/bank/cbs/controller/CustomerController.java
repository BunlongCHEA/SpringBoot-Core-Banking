package com.bank.cbs.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Customers", description = "Customer management APIs, including creation, retrieval, updating, and status management.")
public class CustomerController {
    private final CustomerService customerService;

    @PostMapping
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
}
