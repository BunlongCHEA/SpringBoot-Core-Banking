package com.bank.cbs.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.config.GoKycClientService;
import com.bank.cbs.domain.entity.Address;
import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.domain.enums.CustomerType;
import com.bank.cbs.dto.request.CreateCustomerFromKycRequest;
import com.bank.cbs.dto.request.CreateCustomerRequest;
import com.bank.cbs.dto.request.UpdateCustomerRequest;
import com.bank.cbs.dto.response.CustomerResponse;
import com.bank.cbs.dto.response.GoKycVerifyResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.ConflictException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.AddressRepository;
import com.bank.cbs.repository.jpa.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerCodeGeneratorService customerCodeGeneratorService;

    private final AddressRepository addressRepository;
    private final GoKycClientService goKycClientService;

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

    // Additional methods for handling KYC status updates from Go-Blockchain-KYC can be added here

    // ── KYC-verified customer creation ───────────────────────────

    /**
     * Creates a Customer only after a successful Go_KYC verification.
     *
     * <p>Conditions that MUST be satisfied in the Go_KYC response:
     * <ul>
     *   <li>status   = VERIFIED</li>
     *   <li>userRole = customer</li>
     *   <li>isActive = true</li>
     *   <li>isDeleted = false</li>
     * </ul>
     *
     * On success:
     * <ol>
     *   <li>Inserts a row into {@code customers}
     *       (customer_code = Go_KYC customer_id, customer_type = INDIVIDUAL, status = ACTIVE)</li>
     *   <li>Inserts a row into {@code addresses}</li>
     *   <li>Inserts the mapping into {@code customer_addresses}</li>
     * </ol>
     */
    @Transactional
    public CustomerResponse createFromKyc(CreateCustomerFromKycRequest request) {

        // 1. Call Go_KYC
        GoKycVerifyResponse kyc =
                goKycClientService.verifyCustomer(request.idType(), request.idNumber(), request.bankId());

        // 2. Validate all required conditions
        validateKycResponse(kyc);

        // 3. Guard against duplicate registration
        if (customerRepository.existsByCustomerCode(kyc.customerId())) {
            throw new ConflictException(
                    "Customer already exists with KYC ID: " + kyc.customerId());
        }
        if (kyc.email() != null && customerRepository.existsByEmail(kyc.email())) {
            throw new ConflictException("Email already registered: " + kyc.email());
        }

        // 4. Build and persist Address
        Address address = buildAddress(kyc.address());
        Address savedAddress = addressRepository.save(address);

        // 5. Build and persist Customer
        Customer customer = Customer.builder()
                .customerCode(kyc.customerId())                  // Go_KYC customer_id
                .fullName(kyc.firstName() + " " + kyc.lastName())
                .dateOfBirth(parseDate(kyc.dateOfBirth()))
                .nationalId(kyc.idNumber())
                .email(kyc.email())
                .phone(kyc.phone())
                .status(CustomerStatus.ACTIVE)                   // VERIFIED → ACTIVE
                .customerType(CustomerType.INDIVIDUAL)           // static for now
                .branchId(request.branchId() != null ? request.branchId() : null)
                .build();

        // 6. Link address via @ManyToMany join table
        customer.getAddresses().add(savedAddress);

        Customer saved = customerRepository.save(customer);
        log.info("Customer created via KYC: code={} kycId={}",
                saved.getCustomerCode(), kyc.customerId());
        return CustomerResponse.from(saved);
    }

    // ── Status update (also used by KYC webhook) ─────────────────

    @Transactional
    public void updateStatusByCustomerCode(String customerCode, CustomerStatus status) {
        Customer customer = customerRepository.findByCustomerCode(customerCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with code: " + customerCode));
        customer.setStatus(status);
        customerRepository.save(customer);
        log.info("Customer {} status updated to {} (triggered by KYC webhook)", customerCode, status);
    }

    // ── Private helpers ──────────────────────────────────────────

    private void validateKycResponse(GoKycVerifyResponse kyc) {
        if (!"VERIFIED".equalsIgnoreCase(kyc.status())) {
            throw new BadRequestException(
                    "KYC record is not VERIFIED. Current status: " + kyc.status());
        }
        if (!"customer".equalsIgnoreCase(kyc.userRole())) {
            throw new BadRequestException(
                    "Go_KYC user does not have the 'customer' role.");
        }
        if (kyc.isActive() == null || !kyc.isActive()) {
            throw new BadRequestException("Go_KYC user account is not active.");
        }
        if (kyc.isDeleted() != null && kyc.isDeleted()) {
            throw new BadRequestException("Go_KYC user account has been deleted.");
        }
    }

    private Address buildAddress(GoKycVerifyResponse.GoKycAddressDto dto) {
        if (dto == null) return Address.builder().line1("N/A").city("N/A").countryCode("XX").build();
        return Address.builder()
                .line1(dto.street() != null ? dto.street() : "N/A")
                .city(dto.city()    != null ? dto.city()   : "N/A")
                .stateProvince(dto.state())
                .postalCode(dto.postalCode())
                .countryCode(dto.country() != null ? dto.country().substring(0, Math.min(2, dto.country().length())).toUpperCase() : "XX")
                .isPrimary(true)
                .build();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
