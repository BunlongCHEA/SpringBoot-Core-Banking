package com.bank.cbs.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.entity.Card;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.domain.enums.CardStatus;
import com.bank.cbs.dto.request.CreateCardRequest;
import com.bank.cbs.dto.response.CardResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.CardRepository;
import com.bank.cbs.security.SecurityAuditContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {
    private final CardRepository cardRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;

    @Transactional
    public CardResponse issue(UUID accountId, CreateCardRequest request) {
        Account account = accountService.getOrThrow(accountId);

        String rawCardNumber = generateCardNumber();
        String lastFour      = rawCardNumber.substring(rawCardNumber.length() - 4);
        String hashedNumber  = passwordEncoder.encode(rawCardNumber);

        Card card = Card.builder()
            .account(account)
            .cardNumberHash(hashedNumber)
            .cardLastFour(lastFour)
            .cardType(request.cardType())
            .expiryDate(LocalDate.now().plusYears(4))
            .status(CardStatus.PENDING)
            .dailyLimit(request.dailyLimit())
            .contactlessEnabled(true)
            .internationalEnabled(false)
            .issuedAt(OffsetDateTime.now())
            .build();

        Card saved = cardRepository.save(card);
        log.info("Card issued for account: {} last four: {}", accountId, lastFour);
        return CardResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> findByAccount(UUID accountId) {
        return cardRepository.findByAccount_AccountId(accountId)
            .stream().map(CardResponse::from).toList();
    }

    @Transactional
    public CardResponse block(UUID cardId) {
        Card card = getOrThrow(cardId);
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new BusinessException("Card is already blocked");
        }
        CardStatus oldStatus = card.getStatus();
        card.setStatus(CardStatus.BLOCKED);
        card.setBlockedAt(OffsetDateTime.now());

        auditService.log("Card", cardId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", oldStatus), Map.of("status", "BLOCKED"), null);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public CardResponse unblock(UUID cardId) {
        Card card = getOrThrow(cardId);
        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new BusinessException("Only a BLOCKED card can be unblocked. Current status: " + card.getStatus());
        }
        card.setStatus(CardStatus.ACTIVE);
        card.setBlockedAt(null);

        auditService.log("Card", cardId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", "BLOCKED"), Map.of("status", "ACTIVE"), null);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public CardResponse activate(UUID cardId) {
        Card card = getOrThrow(cardId);
        if (card.getStatus() != CardStatus.INACTIVE && card.getStatus() != CardStatus.PENDING) {
            throw new BusinessException("Card cannot be activated from status: " + card.getStatus());
        }
        card.setStatus(CardStatus.ACTIVE);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public CardResponse deactivate(UUID cardId) {
        Card card = getOrThrow(cardId);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Only an ACTIVE card can be deactivated. Current status: " + card.getStatus());
        }
        card.setStatus(CardStatus.INACTIVE);
        return CardResponse.from(cardRepository.save(card));
    }

    private Card getOrThrow(UUID cardId) {
        return cardRepository.findById(cardId)
            .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardId));
    }

    private String generateCardNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(4000000000000000L, 4999999999999999L));
    }
}
