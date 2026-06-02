package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.PasswordHistory;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {
    /** Returns the last N password hashes for the given user, newest first. */
    @Query("""
            SELECT ph FROM PasswordHistory ph
            WHERE ph.userId = :userId
            ORDER BY ph.createdAt DESC
            LIMIT :limit
           """)
    List<PasswordHistory> findRecentByUserId(UUID userId, int limit);

    /** Prunes old entries, keeping only the most recent :keep rows. */
    @Modifying
    @Query("""
            DELETE FROM PasswordHistory ph
            WHERE ph.userId = :userId
              AND ph.historyId NOT IN (
                  SELECT ph2.historyId FROM PasswordHistory ph2
                  WHERE ph2.userId = :userId
                  ORDER BY ph2.createdAt DESC
                  LIMIT :keep
              )
           """)
    void pruneOldEntries(UUID userId, int keep);
}
