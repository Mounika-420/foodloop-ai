package com.foodloop.service;

import com.foodloop.repository.RevokedTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Expired tokens are rejected by signature check anyway, so their revocation rows can be deleted hourly. */
@Component
public class TokenCleanupScheduler {
    private final RevokedTokenRepository revokedTokenRepository;

    public TokenCleanupScheduler(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpired() {
        revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
