package com.bank.cbs.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.entity.AccountType;
import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.domain.enums.CustomerStatus;
// import com.bank.cbs.domain.enums.AccountType;
import com.bank.cbs.dto.request.CreateAccountRequest;
import com.bank.cbs.dto.response.AccountResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.AccountRepository;
import com.bank.cbs.repository.jpa.AccountTypeRepository;
import com.bank.cbs.repository.jpa.CurrencyRepository;
import com.bank.cbs.security.SecurityAuditContext;
import com.bank.cbs.service.redis.BalanceCacheRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository       accountRepository;
    private final CurrencyRepository      currencyRepository;
    private final CustomerService         customerService;
    private final BalanceCacheRedisService balanceCacheRedisService;
    private final AccountTypeRepository   accountTypeRepository;

    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;

    @Transactional
    public AccountResponse create(UUID customerId, CreateAccountRequest request) {
        AccountType accountType = accountTypeRepository.findById(request.accountTypeId())
        .orElseThrow(() -> new BusinessException("Unknown account type: " + request.accountTypeId()));

        if (!accountType.isActive()) {
            throw new BusinessException("Account type is not active: " + accountType.getCode());
        }
        if (!accountType.isCreditNature()) {
            throw new BusinessException(
                accountType.getName() + " accounts are created automatically through their own workflow " +
                "(e.g. loan disbursement) — not through direct account opening.");
        }
        
        Customer customer = customerService.getOrThrow(customerId);

        var currency = currencyRepository.findById(request.currencyCode())
            .orElseThrow(() -> new ResourceNotFoundException("Currency not found: " + request.currencyCode()));

        Account account = Account.builder()
            .accountNumber(generateAccountNumber())
            .customer(customer)
            .accountType(accountType)
            .currency(currency)
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .holdBalance(BigDecimal.ZERO)
            .status(AccountStatus.ACTIVE)
            .dailyLimit(request.dailyLimit() != null ? request.dailyLimit() : new BigDecimal("50000.0000"))
            .openedAt(OffsetDateTime.now())
            .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: {} for customer: {}", saved.getAccountNumber(), customerId);
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(UUID accountId) {
        return AccountResponse.from(getOrThrow(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findByCustomer(UUID customerId) {
        return accountRepository.findByCustomer_CustomerId(customerId)
            .stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId) {
        return balanceCacheRedisService.get(accountId.toString())
            .orElseGet(() -> {
                BigDecimal balance = getOrThrow(accountId).getAvailableBalance();
                balanceCacheRedisService.cache(accountId.toString(), balance);
                return balance;
            });
    }

    @Transactional
    public void freeze(UUID accountId) {
        Account account = getOrThrow(accountId);
        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);
        balanceCacheRedisService.evict(accountId.toString());

        auditService.log("Account", accountId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", "ACTIVE"), Map.of("status", "FROZEN"), null);
    }

    @Transactional
    public void unfreeze(UUID accountId) {
        Account account = getOrThrow(accountId);
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new BusinessException("Only a FROZEN account can be unfrozen. Current status: " + account.getStatus());
        }
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        balanceCacheRedisService.evict(accountId.toString());

        auditService.log("Account", accountId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", "FROZEN"), Map.of("status", "ACTIVE"), null);
    }

    @Transactional
    public void close(UUID accountId) {
        Account account = getOrThrow(accountId);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Cannot close account with non-zero balance");
        }
        AccountStatus oldStatus = account.getStatus();
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(OffsetDateTime.now());
        accountRepository.save(account);
        balanceCacheRedisService.evict(accountId.toString());

        auditService.log("Account", accountId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", oldStatus), Map.of("status", "CLOSED"), null);
    }

    public Account getOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    // public Account getByAccountNumberOrThrow(String accountNumber) {
    //     return accountRepository.findByAccountNumber(accountNumber)
    //         .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    // }

    @Transactional(readOnly = true)
    public AccountResponse findByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return AccountResponse.from(account);   // mapping now happens while the session is still open
    }

    public String generateAccountNumber() {
        String number;
        do {
            number = String.valueOf(ThreadLocalRandom.current().nextLong(1000000000000000L, 9999999999999999L));
            number = number.substring(0, 16);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
