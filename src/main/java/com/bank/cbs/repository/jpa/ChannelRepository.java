package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.cbs.domain.entity.Channel;

public interface ChannelRepository extends JpaRepository<Channel, UUID>  {
    List<Channel> findByIsActiveTrue();
    Optional<Channel> findByCode(String code);
}
