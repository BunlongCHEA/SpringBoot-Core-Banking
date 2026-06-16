package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    // JpaRepository already provides: save(), findById(), findAll(), deleteById()

    /**
     * All addresses linked to a specific customer via the customer_addresses
     * join table. Primary addresses are returned first.
     *
     * Used by syncAddress() to diff the incoming KYC address against
     * what is already stored, without touching the lazy-loaded Customer.addresses bag.
     */
    @Query("""
            SELECT a
            FROM   Customer c
            JOIN   c.addresses a
            WHERE  c.customerId = :customerId
            ORDER  BY a.isPrimary DESC, a.createdAt ASC
           """)
    List<Address> findByCustomerId(@Param("customerId") UUID customerId);
 
    /**
     * Bulk-demotes every primary address for a customer to is_primary = false.
     *
     * Called before promoting/inserting a new primary so the constraint
     * "only one primary per customer" is always satisfied.
     *
     * clearAutomatically = true flushes the JPA L1 cache after the bulk UPDATE
     * so any subsequent finds in the same transaction see the updated rows.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Address a
            SET    a.isPrimary = false
            WHERE  a IN (
                SELECT addr
                FROM   Customer c
                JOIN   c.addresses addr
                WHERE  c.customerId = :customerId
                  AND  addr.isPrimary = true
            )
           """)
    int demoteAllPrimary(@Param("customerId") UUID customerId);
}
