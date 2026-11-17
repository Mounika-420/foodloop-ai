package com.foodloop.service;

import com.foodloop.entity.Donation;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.PickupRequestRepository;
import jakarta.persistence.EntityManager;
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
 * Expires donations that an NGO accepted but that nobody collected before the food expired.
 * Donations already handed to a volunteer (VOLUNTEER_ASSIGNED and later) are never touched.
 */
@Component
public class AcceptedDonationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcceptedDonationExpiryScheduler.class);

    private final EntityManager em;
    private final DonationRepository donations;
    private final PickupRequestRepository pickups;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public AcceptedDonationExpiryScheduler(EntityManager em,
                                           DonationRepository donations,
                                           PickupRequestRepository pickups,
                                           AuditService auditService,
                                           PlatformTransactionManager txManager) {
        this.em = em;
        this.donations = donations;
        this.pickups = pickups;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void expireOverdue() {
        try {
            int n = expireOverdue(LocalDateTime.now());
            if (n > 0) {
                log.info("Expired {} accepted donation(s) that were never collected", n);
            }
        } catch (RuntimeException e) {
            log.warn("Accepted-donation expiry run failed: {}", e.getMessage());
        }
    }

    int expireOverdue(LocalDateTime now) {
        List<Long> ids = em.createQuery(
                        "select d.id from Donation d where d.status = :status and d.expiresAt < :now", Long.class)
                .setParameter("status", DonationStatus.ACCEPTED)
                .setParameter("now", now)
                .getResultList();
        int expired = 0;
        for (Long id : ids) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> expireOne(id, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire accepted donation {}: {}", id, e.getMessage());
            }
        }
        return expired;
    }

    private boolean expireOne(Long id, LocalDateTime now) {
        Donation d = donations.findById(id).orElse(null);
        if (d == null || d.getStatus() != DonationStatus.ACCEPTED
                || d.getExpiresAt() == null || !d.getExpiresAt().isBefore(now)
                || !d.getStatus().canTransitionTo(DonationStatus.EXPIRED)) {
            return false;
        }
        pickups.findByDonation_Id(id).ifPresent(p -> {
            if (p.getStatus() == PickupStatus.PENDING_ASSIGNMENT || p.getStatus() == PickupStatus.ASSIGNED) {
                p.setStatus(PickupStatus.CANCELLED);
                pickups.save(p);
            }
        });
        d.setStatus(DonationStatus.EXPIRED);
        donations.saveAndFlush(d);
        auditService.log(null, "EXPIRE_DONATION", "DONATION", id, null, Map.of("status", "EXPIRED"));
        return true;
    }
}