package com.bank.cbs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.entity.Channel;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.repository.jpa.ChannelRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/channels")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelRepository channelRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Channel>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(channelRepository.findByIsActiveTrue()));
    }
}
