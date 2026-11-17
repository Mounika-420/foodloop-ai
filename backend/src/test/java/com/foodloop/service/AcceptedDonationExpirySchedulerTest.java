package com.foodloop.service;

import com.foodloop.entity.Donation;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.PickupRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcceptedDonationExpirySchedulerTest {

    @Mock EntityManager em;
    @Mock TypedQuery<Long> query;
    @Mock DonationRepository donations;
    @Mock PickupRequestRepository pickups;
    @Mock AuditService audit;
    @Mock PlatformTransactionManager txm;

    AcceptedDonationExpiryScheduler scheduler;
    final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);

    @BeforeEach
    void setUp() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(pickups.findByDonation_Id(any())).thenReturn(Optional.empty());
        scheduler = new AcceptedDonationExpiryScheduler(em, donations, pickups, audit, txm);
    }

    private Donation donation(long id, DonationStatus status, LocalDateTime expiresAt) {
        Donation d = mock(Donation.class);
        when(d.getId()).thenReturn(id);
        when(d.getStatus()).thenReturn(status);
        when(d.getExpiresAt()).thenReturn(expiresAt);
        when(donations.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private PickupRequest pickup(long donationId, PickupStatus status) {
        PickupRequest p = mock(PickupRequest.class);
        when(p.getStatus()).thenReturn(status);
        when(pickups.findByDonation_Id(donationId)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void expiresAcceptedDonationAndCancelsItsPendingPickup() {
        Donation d = donation(1L, DonationStatus.ACCEPTED, now.minusHours(1));
        PickupRequest p = pickup(1L, PickupStatus.PENDING_ASSIGNMENT);
        when(query.getResultList()).thenReturn(List.of(1L));

        assertEquals(1, scheduler.expireOverdue(now));

        verify(d).setStatus(DonationStatus.EXPIRED);
        verify(donations).saveAndFlush(d);
        verify(p).setStatus(PickupStatus.CANCELLED);
        verify(pickups).save(p);
        verify(audit).log(any(), eq("EXPIRE_DONATION"), eq("DONATION"), eq(1L), any(), any());
    }

    @Test
    void expiresAcceptedDonationThatHasNoPickup() {
        Donation d = donation(1L, DonationStatus.ACCEPTED, now.minusMinutes(1));
        when(query.getResultList()).thenReturn(List.of(1L));

        assertEquals(1, scheduler.expireOverdue(now));

        verify(d).setStatus(DonationStatus.EXPIRED);
        verify(pickups, never()).save(any());
    }

    @Test
    void doesNotCancelAPickupThatAVolunteerAlreadyAccepted() {
        Donation d = donation(1L, DonationStatus.ACCEPTED, now.minusHours(1));
        PickupRequest p = pickup(1L, PickupStatus.VOLUNTEER_ACCEPTED);
        when(query.getResultList()).thenReturn(List.of(1L));

        scheduler.expireOverdue(now);

        verify(p, never()).setStatus(any());
    }

    @Test
    void skipsDonationThatMovedOnAfterTheQuery() {
        Donation d = donation(1L, DonationStatus.VOLUNTEER_ASSIGNED, now.minusHours(1));
        when(query.getResultList()).thenReturn(List.of(1L));

        assertEquals(0, scheduler.expireOverdue(now));

        verify(d, never()).setStatus(any());
        verify(audit, never()).log(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void skipsDonationThatHasNotExpiredYet() {
        Donation d = donation(1L, DonationStatus.ACCEPTED, now.plusHours(1));
        when(query.getResultList()).thenReturn(List.of(1L));

        assertEquals(0, scheduler.expireOverdue(now));

        verify(d, never()).setStatus(any());
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        Donation bad = donation(1L, DonationStatus.ACCEPTED, now.minusHours(1));
        Donation good = donation(2L, DonationStatus.ACCEPTED, now.minusHours(1));
        when(donations.saveAndFlush(bad)).thenThrow(new RuntimeException("boom"));
        when(query.getResultList()).thenReturn(List.of(1L, 2L));

        assertEquals(1, scheduler.expireOverdue(now));

        verify(good).setStatus(DonationStatus.EXPIRED);
    }

    @Test
    void nothingDueDoesNothing() {
        when(query.getResultList()).thenReturn(List.of());

        assertEquals(0, scheduler.expireOverdue(now));

        verify(audit, never()).log(any(), anyString(), anyString(), any(), any(), any());
    }
}