package com.bank.cbs.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.config.KycClientService;
import com.bank.cbs.domain.entity.Address;
import com.bank.cbs.domain.entity.Branch;
import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.domain.enums.CustomerType;
import com.bank.cbs.domain.specification.CustomerSpecifications;
import com.bank.cbs.dto.request.CreateCustomerFromKycRequest;
import com.bank.cbs.dto.request.CreateCustomerRequest;
import com.bank.cbs.dto.request.UpdateCustomerRequest;
import com.bank.cbs.dto.response.CustomerResponse;
import com.bank.cbs.dto.response.GoKycVerifyResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.ConflictException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.AddressRepository;
import com.bank.cbs.repository.jpa.BranchRepository;
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
    private final KycClientService goKycClientService;
    private final BranchRepository branchRepository;

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
        Specification<Customer> spec = Specification
            .where(CustomerSpecifications.withStatus(status))
            .and(CustomerSpecifications.matchingSearch(search));

        return customerRepository.findAll(spec, pageable)
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

    // Used by AccountService and other services to load a managed Customer.
    public Customer getOrThrow(UUID customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
    }

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

        Branch branch = request.branchId() != null
                ? branchRepository.findById(request.branchId()).orElse(null)
                : null;

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
                .branch(branch) // Branch object, not UUID
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

    // ══════════════════════════════════════════════════════════════
    //  Address sync  (2nd / 3rd KYC, or after address change)
    // ══════════════════════════════════════════════════════════════
 
    /**
     * Syncs a customer's address after a new KYC verification.
     * Safe to call repeatedly — idempotent when the address has not changed.
     *
     * <h3>Scenario A — address unchanged</h3>
     * <pre>
     *  addresses            customer_addresses
     *  ─────────────────    ─────────────────────────────
     *  addr-1  primary=true  cust-1 | addr-1   ← untouched
     * </pre>
     * No DB writes at all.
     *
     * <h3>Scenario B — address changed (new address)</h3>
     * <pre>
     *  BEFORE
     *    addr-1  is_primary=true
     *    customer_addresses: cust-1|addr-1
     *
     *  AFTER
     *    addr-1  is_primary=false   ← bulk UPDATE via demoteAllPrimary()
     *    addr-2  is_primary=true    ← INSERT
     *    customer_addresses: cust-1|addr-1, cust-1|addr-2  ← new join row
     * </pre>
     *
     * <h3>Scenario B2 — moved back to a previous address</h3>
     * <pre>
     *  BEFORE
     *    addr-1  is_primary=false  (was primary, then customer moved)
     *    addr-2  is_primary=true
     *    customer_addresses: cust-1|addr-1, cust-1|addr-2
     *
     *  AFTER  (KYC again shows addr-1's values)
     *    addr-1  is_primary=true   ← UPDATE only, no new INSERT
     *    addr-2  is_primary=false
     *    customer_addresses: unchanged  ← no new join row needed
     * </pre>
     *
     * @param customerId CBS customer UUID
     * @param request    idType / idNumber / bankId used to re-verify against Go_KYC
     * @return updated CustomerResponse
     */
    @Transactional
    public CustomerResponse syncAddressFromKyc(UUID customerId, CreateCustomerFromKycRequest request) {
 
        // 1. Load the customer (managed entity — any collection changes are tracked)
        Customer customer = getOrThrow(customerId);
 
        // 2. Re-verify against Go_KYC to get the latest address
        GoKycVerifyResponse kyc =
                goKycClientService.verifyCustomer(request.idType(), request.idNumber(), request.bankId());
        validateKycResponse(kyc);
 
        // 3. Make sure this KYC record belongs to this customer
        if (!customer.getCustomerCode().equals(kyc.customerId())) {
            throw new BadRequestException(
                    "KYC record (customerId=" + kyc.customerId()
                    + ") does not belong to customer " + customerId);
        }
 
        // 4. Sync — handles A / B / B2 transparently
        syncAddress(customer, kyc.address());
 
        log.info("Address synced for customer={} from KYC", customerId);
        return CustomerResponse.from(customerRepository.save(customer));
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

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Core three-way decision:
     * <ol>
     *   <li>Incoming == current primary  →  no-op (Scenario A)</li>
     *   <li>Incoming matches a non-primary that already exists  →  re-promote it (Scenario B2)</li>
     *   <li>Incoming is brand new  →  demote old + insert + link (Scenario B)</li>
     * </ol>
     */
    private void syncAddress(Customer customer, GoKycVerifyResponse.GoKycAddressDto dto) {
        Address incoming = buildAddress(dto);
 
        // Load all existing addresses for this customer (explicit JPQL, avoids lazy-load issues)
        List<Address> existing = addressRepository.findByCustomerId(customer.getCustomerId());
 
        Address currentPrimary = existing.stream()
                .filter(Address::isPrimary)
                .findFirst()
                .orElse(null);
 
        // ── Scenario A: same as current primary → no-op ──────────────────
        if (currentPrimary != null && addressMatches(currentPrimary, incoming)) {
            log.debug("Address unchanged for customer={} — skipping", customer.getCustomerId());
            return;
        }
 
        // Identify early (before demote) if the incoming address already exists
        // as a non-primary record (avoids checking in-memory state after bulk UPDATE)
        Address toRePromote = existing.stream()
                .filter(a -> currentPrimary == null
                        || !a.getAddressId().equals(currentPrimary.getAddressId()))
                .filter(a -> addressMatches(a, incoming))
                .findFirst()
                .orElse(null);
 
        // ── Demote all current primaries (bulk UPDATE, L1 cache cleared) ─
        if (currentPrimary != null) {
            int demoted = addressRepository.demoteAllPrimary(customer.getCustomerId());
            log.debug("Demoted {} primary address(es) for customer={}", demoted, customer.getCustomerId());
        }
 
        if (toRePromote != null) {
            // ── Scenario B2: re-promote an existing non-primary ──────────
            toRePromote.setPrimary(true);
            addressRepository.save(toRePromote);
            log.info("Re-promoted address {} to primary for customer={}",
                    toRePromote.getAddressId(), customer.getCustomerId());
 
        } else {
            // ── Scenario B: brand-new address ────────────────────────────
            incoming.setPrimary(true);
            Address saved = addressRepository.save(incoming);
            // Adding to the managed collection causes JPA to INSERT the customer_addresses row
            customer.getAddresses().add(saved);
            log.info("New address {} inserted and linked to customer={}",
                    saved.getAddressId(), customer.getCustomerId());
        }
    }

    /**
     * Returns true when two addresses represent the same physical location.
     *
     * <h3>Field-by-field rules</h3>
     * <ul>
     *   <li><b>countryCode</b> — exact match after {@link #normalize}.
     *       Now always consistent because the NextJS registration form stores
     *       ISO 3166-1 alpha-2 codes ("KH", "TH" …) rather than full country
     *       names.  CBS {@code buildAddress()} takes {@code substring(0,2)}, so
     *       "KH" → "KH" ✅ (previously "Cambodia" → "CA" ❌).</li>
     *   <li><b>stateProvince / city / postalCode</b> — exact match after
     *       {@link #normalize}.  stateProvince is now driven by a dropdown in
     *       the registration form, so the same province name is always stored
     *       ("Phnom Penh", not "phnom penh" or "PhnomPenh").</li>
     *   <li><b>line1 (street)</b> — matched via {@link #normalizeStreet}, which
     *       expands common abbreviations ("St." → "street"), strips punctuation,
     *       and collapses whitespace before comparing.  This tolerates minor
     *       free-text variants entered at different times:
     *       <pre>
     *         "123 Main St."  →  "123 main street"
     *         "123 Main Street" →  "123 main street"   ← same ✅
     *
     *         "123 Main St."  →  "123 main street"
     *         "456 Main Street" →  "456 main street"   ← different ✅
     *       </pre>
     *   </li>
     * </ul>
     *
     * <p>{@code isPrimary} is deliberately excluded — we compare location only.
     */
    private boolean addressMatches(Address stored, Address incoming) {
        // Non-street fields: exact match after basic normalization
        if (!Objects.equals(normalize(stored.getCity()),          normalize(incoming.getCity())))          return false;
        if (!Objects.equals(normalize(stored.getCountryCode()),   normalize(incoming.getCountryCode())))   return false;
        if (!Objects.equals(normalize(stored.getPostalCode()),    normalize(incoming.getPostalCode())))     return false;
        if (!Objects.equals(normalize(stored.getStateProvince()), normalize(incoming.getStateProvince()))) return false;
        // Street: use abbreviation-aware normalization
        return normalizeStreet(stored.getLine1()).equals(normalizeStreet(incoming.getLine1()));
    }
 
    /**
     * Normalizes a free-text street line for comparison.
     *
     * <ol>
     *   <li>Lowercase + trim</li>
     *   <li>Expand common English / Khmer-influenced abbreviations</li>
     *   <li>Strip leading "No." / "#" house-number prefixes (same number either way)</li>
     *   <li>Replace non-alphanumeric characters with a single space</li>
     *   <li>Collapse multiple spaces</li>
     * </ol>
     *
     * Examples:
     * <pre>
     *   "123 Main St."      → "123 main street"
     *   "No. 123 Main St"   → "123 main street"
     *   "#123, Main Street" → "123 main street"
     *   "271 Blvd. Sothearos" → "271 boulevard sothearos"
     * </pre>
     */
    /**
     * Normalizes a combined street-address string for comparison.
     *
     * <h3>Input format (from RegisterForm 3-part split)</h3>
     * <pre>
     *   "HouseNo, StreetName, District"
     *   "123A, Street 271, Boeng Keng Kang I"
     *   "Unit 5B, Norodom Blvd, Daun Penh"
     *   "Norodom Blvd, Daun Penh"           ← houseNo omitted
     * </pre>
     *
     * <h3>Steps (in order)</h3>
     * <ol>
     *   <li>Lowercase + trim</li>
     *   <li>Expand English abbreviations — longer patterns first</li>
     *   <li>Strip house-number prefixes: "No.", "#"</li>
     *   <li>Strip ASEAN administrative level words
     *       (sangkat, khan, khum, tambon …) so optional prefix does not break match</li>
     *   <li>Remove all non-alphanumeric chars → space
     *       (handles comma sub-field separator from 3-part format)</li>
     *   <li>Collapse multiple spaces</li>
     * </ol>
     *
     * <h3>Match guarantees</h3>
     * <pre>
     *   "123A, St. 271, BKK I"            → "123a street 271 bkk i"
     *   "123A, Street 271, BKK I"         → "123a street 271 bkk i"  ✅
     *
     *   "123A, Blvd. Sothearos, Daun Penh" → "123a boulevard sothearos daun penh"
     *   "123A, Boulevard Sothearos, Daun Penh" → same                 ✅
     *
     *   "No. 123, Street 271, BKK I"      → "123 street 271 bkk i"
     *   "#123, Street 271, BKK I"         → "123 street 271 bkk i"   ✅
     *
     *   "Sangkat Boeng Keng Kang I"       → "boeng keng kang i"
     *   "Boeng Keng Kang I"               → "boeng keng kang i"      ✅
     * </pre>
     */
    private String normalizeStreet(String s) {
        if (s == null) return "";
        String r = s.trim().toLowerCase();
 
        // ── Step 1: Expand abbreviations (longer patterns first) ──────────────
        r = r.replaceAll("\\bblvds?\\.?\\b",  "boulevard")   // blvd / blvds
             .replaceAll("\\bpkwy?\\.?\\b",   "parkway")     // pkwy / pky
             .replaceAll("\\bfwy\\.?\\b",     "freeway")
             .replaceAll("\\bexpy?\\.?\\b",   "expressway")  // expy / exp
             .replaceAll("\\bhwy\\.?\\b",     "highway")
             .replaceAll("\\bave?\\.?\\b",    "avenue")      // ave / av
             .replaceAll("\\bst\\.?\\b",      "street")      // st  (after blvd)
             .replaceAll("\\brd\\.?\\b",      "road")
             .replaceAll("\\bdr\\.?\\b",      "drive")
             .replaceAll("\\bln\\.?\\b",      "lane")
             .replaceAll("\\bct\\.?\\b",      "court")
             .replaceAll("\\bpl\\.?\\b",      "place")
             .replaceAll("\\bsq\\.?\\b",      "square")
             .replaceAll("\\btrl?\\.?\\b",    "trail");      // trl / tr
 
        // ── Step 2: Strip house-number / address prefixes ─────────────────────
        // Anchored to start-of-string or whitespace to avoid false matches
        // ("know" must NOT become "kw").
        r = r.replaceAll("(?:^|(?<=\\s))no\\.?\\s*", " ")  // "No. 123" → " 123"
             .replaceAll("#\\s*",                     "");   // "#123"    → "123"
 
        // ── Step 3: Strip ASEAN administrative level prefixes ─────────────────
        // Makes "Sangkat BKK I" ≈ "BKK I", "Tambon Mueang" ≈ "Mueang".
        // These words are sometimes typed, sometimes omitted — stripping them
        // prevents a false mismatch when the same district is written both ways.
        r = r.replaceAll("\\bsangkat\\b", "")   // KH: commune / ward (urban)
             .replaceAll("\\bkhan\\b",    "")   // KH: district (Phnom Penh)
             .replaceAll("\\bkhum\\b",    "")   // KH: commune (rural)
             .replaceAll("\\bsrok\\b",    "")   // KH: district (rural)
             .replaceAll("\\btambon\\b",  "")   // TH: sub-district
             .replaceAll("\\bamphoe\\b",  "")   // TH: district
             .replaceAll("\\bphuong\\b",  "")   // VN: ward
             .replaceAll("\\bquan\\b",    "")   // VN: urban district
             .replaceAll("\\bhuyen\\b",   "");  // VN: rural district
 
        // ── Step 4: Remove non-alphanumeric; collapse spaces ──────────────────
        // Commas from "HouseNo, StreetName, District" format become spaces,
        // leaving only meaningful numeric/alphabetic tokens for comparison.
        r = r.replaceAll("[^a-z0-9]", " ")
             .replaceAll("\\s+",      " ")
             .trim();
 
        return r;
    }

    // Null-safe, trimmed, lower-cased — for city / state / country / postal comparisons.
    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private Address buildAddress(GoKycVerifyResponse.GoKycAddressDto dto) {
        if (dto == null) {
            return Address.builder()
                    .line1("N/A").city("N/A").countryCode("XX").isPrimary(true)
                    .build();
        }
        return Address.builder()
                .line1(dto.street()    != null ? dto.street()   : "N/A")
                .city(dto.city()       != null ? dto.city()     : "N/A")
                .stateProvince(dto.state())
                .postalCode(dto.postalCode())
                .countryCode(dto.country() != null
                        ? dto.country().substring(0, Math.min(2, dto.country().length())).toUpperCase()
                        : "XX")
                .isPrimary(true)
                .build();
    }

    
}
