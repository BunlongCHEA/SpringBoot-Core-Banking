package com.bank.cbs.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.AuditLogResponse;
import com.bank.cbs.dto.response.PageResponse;
import com.bank.cbs.service.AuditService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','AUDITOR')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = auditService.search(entityType, entityId, action, changedBy, from, to,
            PageRequest.of(page, size, Sort.by("changedAt").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result.map(AuditLogResponse::from))));
    }
}
