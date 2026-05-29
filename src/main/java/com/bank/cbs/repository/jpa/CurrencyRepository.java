package com.bank.cbs.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Currency;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {
    List<Currency> findByIsActiveTrue();
}
