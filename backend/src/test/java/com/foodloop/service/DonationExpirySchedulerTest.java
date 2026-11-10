package com.foodloop.service;

import com.foodloop.entity.Donation;
import com.foodloop.entity.DonationMatch;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.MatchStatus;
import com.foodloop.repository.DonationMatchRepository;
import com.foodloop.repository.DonationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DonationExpirySchedulerTest {

    @Mock DonationRepository donationRepository;
    @Mock DonationMatchRepository matchRepository;
    @Mock AuditService auditService;
    @Mock PlatformTransactionManager txManager;

    DonationExpiryScheduler scheduler;
    final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);

    @BeforeEach
    void setUp() {
        scheduler = new DonationExpiryScheduler(donationRepository, matchRepository, auditService, txManager);
    }

    private Donation donation(long id, DonationStatus status, LocalDateTime expiresAt) {
        Donation d = mock(Donation.class);
        when(d.getId()).thenReturn(id);
        when(d.getStatus()).thenReturn(status);
        when(d.getExpiresAt()).thenReturn(expiresAt);
        when(donationRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private void due(Donation... donations) {
        when(donationRepository.findByStatusInAndExpiresAtBefore(anyCollection(), eq(now)))
                .thenReturn(List.of(donations));
    }

    @Test
    void expiresAvailableDonationAndItsOpenOffers() {
        Donation d = donation(1L, DonationStatus.AVAILABLE, now.minusMinutes(1));
        DonationMatch m = mock(DonationMatch.class);
        when(matchRepository.findByDonation_IdAndStatus(1L, MatchStatus.NOTIFIED)).thenReturn(List.of(m));
        due(d);

        assertEquals(1, scheduler.expireOverdue(now));

        verify(d).setStatus(DonationStatus.EXPIRED);
        verify(m).setStatus(MatchStatus.EXPIRED);
        verify(m).setRespondedAt(now);
        verify(auditService).log(isNull(), eq("EXPIRE_DONATION"), eq("DONATION"), eq(1L), isNull(), any());
    }

    @Test
    void expiresMatchedDonation() {
        Donation d = donation(2L, DonationStatus.MATCHED, now.minusHours(3));
        when(matchRepository.findByDonation_IdAndStatus(2L, MatchStatus.NOTIFIED)).thenReturn(List.of());
        due(d);

        assertEquals(1, scheduler.expireOverdue(now));

        verify(d).setStatus(DonationStatus.EXPIRED);
    }

    @Test
    void leavesDonationAloneIfAnNgoAcceptedItInTheMeantime() {
        Donation d = donation(3L, DonationStatus.ACCEPTED, now.minusMinutes(1));
        due(d);

        assertEquals(0, scheduler.expireOverdue(now));

        verify(d, never()).setStatus(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void leavesDonationAloneIfItIsNotExpiredAtRecheck() {
        Donation d = donation(4L, DonationStatus.AVAILABLE, now.plusMinutes(10));
        due(d);

        assertEquals(0, scheduler.expireOverdue(now));

        verify(d, never()).setStatus(any());
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        Donation bad = donation(5L, DonationStatus.AVAILABLE, now.minusMinutes(1));
        Donation good = donation(6L, DonationStatus.AVAILABLE, now.minusMinutes(1));
        when(matchRepository.findByDonation_IdAndStatus(any(), eq(MatchStatus.NOTIFIED))).thenReturn(List.of());
        when(donationRepository.save(bad)).thenThrow(new RuntimeException("boom"));
        due(bad, good);

        assertEquals(1, scheduler.expireOverdue(now));

        verify(good).setStatus(DonationStatus.EXPIRED);
    }

    @Test
    void doesNothingWhenNothingIsDue() {
        due();

        assertEquals(0, scheduler.expireOverdue(now));

        verifyNoInteractions(auditService);
    }
}