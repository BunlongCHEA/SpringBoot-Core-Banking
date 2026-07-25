package com.bank.cbs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.entity.SystemSetting;
import com.bank.cbs.dto.request.UpdateSettingRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.SystemSettingRepository;
import com.bank.cbs.service.SystemSettingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SystemSettingController {
    private final SystemSettingRepository repository;
    private final SystemSettingService settingService;

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemSetting>> get(@PathVariable("key") String key) {
        return ResponseEntity.ok(ApiResponse.ok(
            repository.findById(key).orElseThrow(() -> new ResourceNotFoundException("Unknown setting: " + key))));
    }

    @PatchMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable("key") String key, @RequestBody UpdateSettingRequest body) {
        settingService.set(key, body.value());
        return ResponseEntity.ok(ApiResponse.ok("Setting updated", null));
    }
}
