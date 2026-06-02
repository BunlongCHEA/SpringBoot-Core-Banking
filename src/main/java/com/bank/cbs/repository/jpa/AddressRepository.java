package com.bank.cbs.repository.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    // JpaRepository already provides: save(), findById(), findAll(), deleteById()
}
