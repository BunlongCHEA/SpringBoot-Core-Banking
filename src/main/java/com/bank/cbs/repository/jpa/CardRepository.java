package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Card;
import com.bank.cbs.domain.enums.CardStatus;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByAccount_AccountId(UUID accountId);
    Optional<Card> findByCardNumberHash(String cardNumberHash);
    List<Card> findByAccount_AccountIdAndStatus(UUID accountId, CardStatus status);
}
