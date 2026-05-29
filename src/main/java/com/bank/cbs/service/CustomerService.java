package com.bank.cbs.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.dto.request.CreateCustomerRequest;
import com.bank.cbs.dto.request.UpdateCustomerRequest;
import com.bank.cbs.dto.response.CustomerResponse;
import com.bank.cbs.exception.ConflictException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerCodeGeneratorService customerCodeGeneratorService;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        if (customerRepository.existsByPhone(request.phone())) {
            throw new ConflictException("Phone already registered: " + request.phone());
        }
        if (request.nationalId() != null && customerRepository.existsByNationalId(request.nationalId())) {
            throw new ConflictException("National ID already registered: " + request.nationalId());
        }

        Customer customer = Customer.builder()
            .customerCode(customerCodeGeneratorService.generate())
            .fullName(request.fullName())
            .dateOfBirth(request.dateOfBirth())
            .nationalId(request.nationalId())
            .email(request.email())
            .phone(request.phone())
            .customerType(request.customerType())
            .status(CustomerStatus.ACTIVE)
            .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer created: {}", saved.getCustomerCode());
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID customerId) {
        return CustomerResponse.from(getOrThrow(customerId));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(CustomerStatus status, String search, Pageable pageable) {
        return customerRepository.searchCustomers(status, search, pageable)
            .map(CustomerResponse::from);
    }

    @Transactional
    public CustomerResponse update(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = getOrThrow(customerId);
        if (request.fullName()    != null) customer.setFullName(request.fullName());
        if (request.dateOfBirth() != null) customer.setDateOfBirth(request.dateOfBirth());
        if (request.phone()       != null) customer.setPhone(request.phone());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void updateStatus(UUID customerId, CustomerStatus status) {
        Customer customer = getOrThrow(customerId);
        customer.setStatus(status);
        customerRepository.save(customer);
        log.info("Customer {} status updated to {}", customerId, status);
    }

    public Customer getOrThrow(UUID customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
    }
}
