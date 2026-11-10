package com.foodloop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.entity.Donation;
import com.foodloop.entity.DonationMatch;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.Location;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.User;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.MatchStatus;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DonationMatchRepository;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.NgoRequirementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingServiceTest {

    @Mock DonationMatchRepository matchRepository;
    @Mock DonationRepository donationRepository;
    @Mock NgoProfileRepository ngoProfileRepository;
    @Mock NgoRequirementRepository requirementRepository;
    @Mock AuditService auditService;

    MatchingService service;

    @BeforeEach
    void setUp() {
        service = new MatchingService(matchRepository, donationRepository, ngoProfileRepository,
                requirementRepository, auditService, new ObjectMapper());
        when(matchRepository.save(any(DonationMatch.class))).thenAnswer(i -> i.getArgument(0));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(donationRepository.saveAndFlush(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private NgoProfile ngo(long id, double lat, double lon, UserStatus status, String dailyKg) {
        Location loc = new Location();
        loc.setLatitude(BigDecimal.valueOf(lat));
        loc.setLongitude(BigDecimal.valueOf(lon));
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        NgoProfile n = new NgoProfile();
        n.setUserId(id);
        n.setUser(u);
        n.setLocation(loc);
        n.setOrganizationName("NGO " + id);
        n.setDailyCapacityKg(new BigDecimal(dailyKg));
        n.setAvailableFrom(LocalTime.MIN);
        n.setAvailableTo(LocalTime.of(23, 59, 59));
        n.setVerificationStatus(VerificationStatus.VERIFIED);
        return n;
    }

    private Donation donation(DonationStatus status, LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now();
        FoodCategory cat = new FoodCategory();
        cat.setId(1);
        cat.setName("Cooked Meals");
        Location loc = new Location();
        loc.setLatitude(new BigDecimal("11.0"));
        loc.setLongitude(new BigDecimal("76.95"));
        Donation d = new Donation();
        d.setId(4L);
        d.setStatus(status);
        d.setCategory(cat);
        d.setQuantityKg(new BigDecimal("20.00"));
        d.setPickupLocation(loc);
        d.setPickupWindowStart(now.plusHours(1));
        d.setPickupWindowEnd(now.plusHours(4));
        d.setExpiresAt(expiresAt);
        return d;
    }

    private Donation freshDonation(DonationStatus status) {
        return donation(status, LocalDateTime.now().plusHours(10));
    }

    private DonationMatch offer(long id, NgoProfile n, Donation d, MatchStatus status) {
        DonationMatch m = new DonationMatch();
        m.setId(id);
        m.setNgo(n);
        m.setDonation(d);
        m.setStatus(status);
        m.setRankPosition(1);
        when(matchRepository.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void matchDonationCreatesRankedOffersAndMarksMatched() {
        Donation d = freshDonation(DonationStatus.AVAILABLE);
        when(ngoProfileRepository.findByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(List.of(
                ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"),
                ngo(7, 11.1, 76.95, UserStatus.ACTIVE, "100"),
                ngo(8, 11.0, 76.95, UserStatus.SUSPENDED, "100")));

        int n = service.matchDonation(d);

        assertEquals(2, n);
        assertEquals(DonationStatus.MATCHED, d.getStatus());
        ArgumentCaptor<DonationMatch> cap = ArgumentCaptor.forClass(DonationMatch.class);
        verify(matchRepository, times(2)).save(cap.capture());
        assertEquals(1, cap.getAllValues().get(0).getRankPosition());
        assertEquals(6L, cap.getAllValues().get(0).getNgo().getUserId());
        assertEquals(MatchStatus.NOTIFIED, cap.getAllValues().get(0).getStatus());
        assertTrue(cap.getAllValues().get(0).getScoreBreakdown().contains("distance"));
    }

    @Test
    void matchDonationLeavesDonationAvailableWhenNoNgoFits() {
        Donation d = freshDonation(DonationStatus.AVAILABLE);
        when(ngoProfileRepository.findByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(List.of());
        assertEquals(0, service.matchDonation(d));
        assertEquals(DonationStatus.AVAILABLE, d.getStatus());
    }

    @Test
    void matchDonationIgnoresDonationsThatAreNotAvailable() {
        Donation d = freshDonation(DonationStatus.CANCELLED);
        assertEquals(0, service.matchDonation(d));
        verify(matchRepository, never()).save(any(DonationMatch.class));
    }

    @Test
    void acceptMarksAcceptedAndWithdrawsOtherOffers() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        NgoProfile mine = ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100");
        DonationMatch m = offer(1, mine, d, MatchStatus.NOTIFIED);
        DonationMatch other = offer(2, ngo(7, 11.1, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        when(matchRepository.findByDonation_IdAndStatus(4L, MatchStatus.NOTIFIED)).thenReturn(List.of(m, other));

        service.accept(6L, 1L);

        assertEquals(DonationStatus.ACCEPTED, d.getStatus());
        assertEquals(6L, d.getAcceptedNgo().getUserId());
        assertEquals(MatchStatus.ACCEPTED, m.getStatus());
        assertEquals(MatchStatus.WITHDRAWN, other.getStatus());
    }

    @Test
    void acceptFailsWhenDonationWasAlreadyTaken() {
        Donation d = freshDonation(DonationStatus.ACCEPTED);
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        assertThrows(ConflictException.class, () -> service.accept(6L, 1L));
    }

    @Test
    void acceptFailsWhenDonationHasExpired() {
        Donation d = donation(DonationStatus.MATCHED, LocalDateTime.now().minusMinutes(5));
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        assertThrows(ConflictException.class, () -> service.accept(6L, 1L));
    }

    @Test
    void acceptFailsWhenOverDailyCapacity() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "10"), d, MatchStatus.NOTIFIED);
        assertThrows(ConflictException.class, () -> service.accept(6L, 1L));
    }

    @Test
    void acceptByAnotherNgoIsNotFound() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        assertThrows(ResourceNotFoundException.class, () -> service.accept(7L, 1L));
    }

    @Test
    void acceptingTwiceIsAConflict() {
        Donation d = freshDonation(DonationStatus.ACCEPTED);
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.ACCEPTED);
        assertThrows(ConflictException.class, () -> service.accept(6L, 1L));
    }

    @Test
    void losingTheOptimisticLockRaceIsAConflict() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        when(donationRepository.saveAndFlush(any(Donation.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Donation.class, 4L));
        assertThrows(ConflictException.class, () -> service.accept(6L, 1L));
    }

    @Test
    void rejectKeepsDonationMatchedWhileOtherOffersArePending() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        DonationMatch m = offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        DonationMatch other = offer(2, ngo(7, 11.1, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        when(matchRepository.findByDonation_IdAndStatus(4L, MatchStatus.NOTIFIED)).thenReturn(List.of(m, other));

        service.reject(6L, 1L, "Too far");

        assertEquals(MatchStatus.REJECTED, m.getStatus());
        assertEquals("Too far", m.getRejectionReason());
        assertEquals(DonationStatus.MATCHED, d.getStatus());
    }

    @Test
    void rejectLastOfferMovesDonationToRejectedAll() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        DonationMatch m = offer(1, ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100"), d, MatchStatus.NOTIFIED);
        when(matchRepository.findByDonation_IdAndStatus(4L, MatchStatus.NOTIFIED)).thenReturn(List.of(m));

        service.reject(6L, 1L, "   ");

        assertEquals(DonationStatus.REJECTED_ALL, d.getStatus());
        assertEquals(null, m.getRejectionReason());
    }

    @Test
    void ngoSeesDonationOnlyWhileItHasALiveOffer() {
        Donation d = freshDonation(DonationStatus.MATCHED);
        NgoProfile n = ngo(6, 11.0, 76.95, UserStatus.ACTIVE, "100");
        DonationMatch m = offer(1, n, d, MatchStatus.NOTIFIED);
        when(matchRepository.findByDonation_IdAndNgo_UserId(4L, 6L)).thenReturn(Optional.of(m));
        assertTrue(service.hasOffer(4L, 6L));

        m.setStatus(MatchStatus.WITHDRAWN);
        assertFalse(service.hasOffer(4L, 6L));
        assertFalse(service.hasOffer(4L, 99L));
    }
}