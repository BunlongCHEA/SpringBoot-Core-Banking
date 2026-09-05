package com.bank.cbs.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.UserRole;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Page<User> findByRoleAndIsActive(UserRole role, boolean isActive, Pageable pageable);
    Page<User> findAll(Pageable pageable);
    Optional<User> findByUsernameAndIsDeletedFalse(String username);
    Optional<User> findByUserIdAndIsDeletedFalse(UUID userId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByRoleAndIsDeletedFalse(UserRole role);
}
