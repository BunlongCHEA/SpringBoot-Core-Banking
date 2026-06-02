package com.bank.cbs.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndIsDeletedFalse(String username);
    Optional<User> findByUserIdAndIsDeletedFalse(UUID userId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
