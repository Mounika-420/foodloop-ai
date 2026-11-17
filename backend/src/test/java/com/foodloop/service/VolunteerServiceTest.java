package com.foodloop.service;

import com.foodloop.dto.AvailabilityRequest;
import com.foodloop.dto.AvailabilityResponse;
import com.foodloop.entity.Donation;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.PickupRequestRepository;
import com.foodloop.repository.VolunteerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VolunteerServiceTest {

    @Mock VolunteerProfileRepository profiles;
    @Mock PickupRequestRepository pickups;
    @Mock AuditService auditService;
    @InjectMocks VolunteerService service;

    private VolunteerProfile profile;

    @BeforeEach
    void setUp() {
        profile = new VolunteerProfile();
        profile.setUserId(9L);
        profile.setAvailabilityStatus(Availability.AVAILABLE);
        when(profiles.findById(9L)).thenReturn(Optional.of(profile));
    }

    private PickupRequest pickupWithDonation(DonationStatus status) {
        PickupRequest pickup = mock(PickupRequest.class);
        Donation donation = mock(Donation.class);
        when(pickup.getDonation()).thenReturn(donation);
        when(donation.getStatus()).thenReturn(status);
        return pickup;
    }

    private void givenOpenPickup(DonationStatus status) {
        PickupRequest pickup = pickupWithDonation(status);
        when(pickups.findByVolunteer_UserIdAndStatusInOrderByScheduledPickupAtAsc(eq(9L), any()))
                .thenReturn(List.of(pickup));
    }

    @Test
    void getReturnsCurrentAvailability() {
        profile.setAvailabilityStatus(Availability.BUSY);
        assertEquals(Availability.BUSY, service.get(9L).availability());
    }

    @Test
    void updateChangesStatusSavesAndAudits() {
        profile.setAvailabilityStatus(Availability.OFFLINE);

        AvailabilityResponse res = service.update(9L, new AvailabilityRequest(Availability.AVAILABLE));

        assertEquals(Availability.AVAILABLE, res.availability());
        assertEquals(Availability.AVAILABLE, profile.getAvailabilityStatus());
        verify(profiles).save(profile);
        verify(auditService).log(eq(9L), eq("UPDATE_AVAILABILITY"), eq("VOLUNTEER"), eq(9L), any(), any());
    }

    @Test
    void sameStatusIsNoOp() {
        service.update(9L, new AvailabilityRequest(Availability.AVAILABLE));

        verify(profiles, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cannotGoOfflineWithActiveDelivery() {
        givenOpenPickup(DonationStatus.IN_TRANSIT);

        assertThrows(ConflictException.class,
                () -> service.update(9L, new AvailabilityRequest(Availability.OFFLINE)));
        assertEquals(Availability.AVAILABLE, profile.getAvailabilityStatus());
        verify(profiles, never()).save(any());
    }

    @Test
    void canGoOfflineWhenDeliveryAlreadyDelivered() {
        givenOpenPickup(DonationStatus.DELIVERED);

        AvailabilityResponse res = service.update(9L, new AvailabilityRequest(Availability.OFFLINE));

        assertEquals(Availability.OFFLINE, res.availability());
    }

    @Test
    void canGoBusyWithActiveDelivery() {
        givenOpenPickup(DonationStatus.VOLUNTEER_ASSIGNED);

        AvailabilityResponse res = service.update(9L, new AvailabilityRequest(Availability.BUSY));

        assertEquals(Availability.BUSY, res.availability());
    }

    @Test
    void missingProfileGives404() {
        when(profiles.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(99L, new AvailabilityRequest(Availability.AVAILABLE)));
    }
}