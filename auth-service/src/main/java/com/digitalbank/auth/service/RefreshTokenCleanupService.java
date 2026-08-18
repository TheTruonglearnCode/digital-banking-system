package com.digitalbank.auth.service;

import com.digitalbank.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired refresh tokens");
        int deleted = refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Deleted {} expired refresh tokens", deleted);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupRevokedTokens() {
        log.info("Starting cleanup of revoked and expired refresh tokens");
        int deleted = refreshTokenRepository.deleteRevokedAndExpiredTokens(LocalDateTime.now());
        log.info("Deleted {} revoked and expired refresh tokens", deleted);
    }
}
