package com.foodloop.service;

import com.foodloop.entity.Donation;
import com.foodloop.entity.DonationMatch;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.MatchStatus;
import com.foodloop.repository.DonationMatchRepository;
import com.foodloop.repository.DonationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Moves donations whose food has expired to EXPIRED and closes their open offers.
 * Runs every 5 minutes. ACCEPTED donations are left alone: an NGO already committed to them.
 */
@Component
public class DonationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DonationExpiryScheduler.class);
    private static final List<DonationStatus> EXPIRABLE = List.of(
            DonationStatus.AVAILABLE, DonationStatus.MATCHED, DonationStatus.REJECTED_ALL);

    private final DonationRepository donationRepository;
    private final DonationMatchRepository matchRepository;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public DonationExpiryScheduler(DonationRepository donationRepository,
                                   DonationMatchRepository matchRepository,
                                   AuditService auditService,
                                   PlatformTransactionManager transactionManager) {
        this.donationRepository = donationRepository;
        this.matchRepository = matchRepository;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void expireOverdue() {
        int n = expireOverdue(LocalDateTime.now());
        if (n > 0) {
            log.info("Expired {} donation(s)", n);
        }
    }

    /** Returns how many donations were moved to EXPIRED. One failure does not stop the others. */
    int expireOverdue(LocalDateTime now) {
        int count = 0;
        for (Donation due : donationRepository.findByStatusInAndExpiresAtBefore(EXPIRABLE, now)) {
            Long id = due.getId();
            try {
                Boolean done = tx.execute(status -> expireOne(id, now));
                if (Boolean.TRUE.equals(done)) {
                    count++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire donation {}: {}", id, ex.getMessage());
            }
        }
        return count;
    }

    /** Re-reads the donation inside the transaction, because an NGO may have accepted it meanwhile. */
    private boolean expireOne(Long id, LocalDateTime now) {
        Donation d = donationRepository.findById(id).orElse(null);
        if (d == null
                || !EXPIRABLE.contains(d.getStatus())
                || !d.getExpiresAt().isBefore(now)
                || !d.getStatus().canTransitionTo(DonationStatus.EXPIRED)) {
            return false;
        }
        for (DonationMatch m : matchRepository.findByDonation_IdAndStatus(id, MatchStatus.NOTIFIED)) {
            m.setStatus(MatchStatus.EXPIRED);
            m.setRespondedAt(now);
            matchRepository.save(m);
        }
        d.setStatus(DonationStatus.EXPIRED);
        donationRepository.save(d);
        auditService.log(null, "EXPIRE_DONATION", "DONATION", id, null, Map.of("status", "EXPIRED"));
        return true;
    }
}