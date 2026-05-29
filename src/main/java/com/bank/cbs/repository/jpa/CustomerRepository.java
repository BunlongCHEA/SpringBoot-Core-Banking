package com.bank.cbs.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByNationalId(String nationalId);
    Optional<Customer> findByCustomerCode(String customerCode);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);
    boolean existsByCustomerCode(String customerCode);

    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:search IS NULL OR
               LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               c.email LIKE LOWER(CONCAT('%', :search, '%')) OR
               c.phone LIKE CONCAT('%', :search, '%'))
        """)
    Page<Customer> searchCustomers(
        @Param("status") CustomerStatus status,
        @Param("search") String search,
        Pageable pageable
    );
}
