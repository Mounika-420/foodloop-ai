package com.foodloop.service;

import com.foodloop.entity.Delivery;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.repository.DeliveryRepository;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.PickupRequestRepository;
import com.foodloop.repository.VolunteerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PickupCancellationTest {

    @Mock PickupRequestRepository pickups;
    @Mock DeliveryRepository deliveries;
    @Mock DonationRepository donations;
    @Mock VolunteerProfileRepository volunteers;
    @Mock AuditService auditService;

    PickupService service;

    @BeforeEach
    void setUp() {
        service = new PickupService(pickups, deliveries, donations, volunteers, auditService);
    }

    private PickupRequest pickup(PickupStatus status) {
        PickupRequest p = new PickupRequest();
        p.setId(1L);
        p.setStatus(status);
        when(pickups.findByDonation_Id(4L)).thenReturn(Optional.of(p));
        return p;
    }

    private Delivery delivery(PickupRequest p, DeliveryStatus status) {
        Delivery d = new Delivery();
        d.setId(3L);
        d.setPickupRequest(p);
        d.setStatus(status);
        when(deliveries.findByPickupRequest_Id(1L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void pendingPickupIsCancelled() {
        PickupRequest p = pickup(PickupStatus.PENDING_ASSIGNMENT);
        service.cancelForDonation(4L);
        assertEquals(PickupStatus.CANCELLED, p.getStatus());
        verify(pickups).save(p);
    }

    @Test
    void acceptedPickupIsCancelledAndItsDeliveryFails() {
        PickupRequest p = pickup(PickupStatus.VOLUNTEER_ACCEPTED);
        Delivery d = delivery(p, DeliveryStatus.NOT_STARTED);
        service.cancelForDonation(4L);
        assertEquals(PickupStatus.CANCELLED, p.getStatus());
        assertEquals(DeliveryStatus.FAILED, d.getStatus());
        assertNotNull(d.getFailureReason());
    }

    @Test
    void pickedUpPickupIsLeftAlone() {
        PickupRequest p = pickup(PickupStatus.PICKED_UP);
        service.cancelForDonation(4L);
        assertEquals(PickupStatus.PICKED_UP, p.getStatus());
        verify(pickups, never()).save(any(PickupRequest.class));
    }

    @Test
    void noPickupMeansNothingToDo() {
        when(pickups.findByDonation_Id(9L)).thenReturn(Optional.empty());
        service.cancelForDonation(9L);
        verify(pickups, never()).save(any(PickupRequest.class));
    }
}