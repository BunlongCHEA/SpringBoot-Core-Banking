package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.TransactionIdempotency;
import com.bank.cbs.exception.DuplicateTransactionException;
import com.bank.cbs.repository.jpa.TransactionIdempotencyRepository;
import com.bank.cbs.service.redis.IdempotencyRedisService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final TransactionIdempotencyRepository idempotencyRepository; // durable — source of truth
    private final IdempotencyRedisService redisCache;                    // fast-path cache only

    @Transactional(readOnly = true)
    public boolean exists(String idempotencyKey) {
        if (redisCache.exists(idempotencyKey)) return true;
        // Redis miss doesn't mean "new" — TTL may have expired. Check Postgres.
        return idempotencyRepository.existsById(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<String> getTransactionId(String idempotencyKey) {
        return redisCache.getTransactionId(idempotencyKey)
            .or(() -> idempotencyRepository.findById(idempotencyKey)
                .map(r -> r.getTransactionId().toString()));
    }

    @Transactional
    public void save(String idempotencyKey, UUID transactionId, OffsetDateTime initiatedAt) {
        // Durable write first, inside the SAME DB transaction as the transfer/deposit/withdrawal —
        // if that transaction rolls back, this row rolls back with it.
        
        // idempotencyRepository.save(TransactionIdempotency.builder()
        //     .idempotencyKey(idempotencyKey)
        //     .transactionId(transactionId)
        //     .initiatedAt(initiatedAt)
        //     .build());
        try {
            idempotencyRepository.save(TransactionIdempotency.builder()
            .idempotencyKey(idempotencyKey)
            .transactionId(transactionId)
            .initiatedAt(initiatedAt)
            .build());
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTransactionException("Duplicate request: " + idempotencyKey);
        }

        // Best-effort cache populate — fine if this part is eventually lost, Postgres backs it up.
        redisCache.save(idempotencyKey, transactionId.toString());
    }
}
