package com.foodloop.service;

import com.foodloop.dto.HistoryItemResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.Delivery;
import com.foodloop.entity.Donation;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HistoryServiceTest {

    @Mock DeliveryRepository deliveries;
    @InjectMocks HistoryService service;

    @Captor ArgumentCaptor<Collection<DeliveryStatus>> statusCaptor;
    @Captor ArgumentCaptor<Pageable> pageableCaptor;

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        Donation donation = mock(Donation.class);
        when(donation.getId()).thenReturn(6L);
        when(donation.getQuantityKg()).thenReturn(new BigDecimal("41.20"));

        NgoProfile ngo = mock(NgoProfile.class);
        when(ngo.getUserId()).thenReturn(6L);

        VolunteerProfile volunteer = mock(VolunteerProfile.class);
        when(volunteer.getUserId()).thenReturn(9L);

        PickupRequest pickup = mock(PickupRequest.class);
        when(pickup.getId()).thenReturn(4L);
        when(pickup.getDonation()).thenReturn(donation);
        when(pickup.getNgo()).thenReturn(ngo);
        when(pickup.getVolunteer()).thenReturn(volunteer);

        delivery = mock(Delivery.class);
        when(delivery.getId()).thenReturn(3L);
        when(delivery.getPickupRequest()).thenReturn(pickup);
        when(delivery.getStatus()).thenReturn(DeliveryStatus.DELIVERED);
        when(delivery.getDeliveredAt()).thenReturn(LocalDateTime.of(2026, 11, 17, 11, 44));

        var page = new PageImpl<>(List.of(delivery), PageRequest.of(0, 20), 1);
        when(deliveries.findByPickupRequest_Volunteer_UserIdAndStatusIn(any(), any(), any())).thenReturn(page);
        when(deliveries.findByPickupRequest_Ngo_UserIdAndStatusIn(any(), any(), any())).thenReturn(page);
    }

    @Test
    void volunteerHistoryMapsItemsAndUsesAllFinishedStatuses() {
        PagedResult<HistoryItemResponse> res = service.forVolunteer(9L, null, 0, 20);

        assertEquals(1, res.totalElements());
        HistoryItemResponse item = res.content().get(0);
        assertEquals(3L, item.deliveryId());
        assertEquals(4L, item.pickupId());
        assertEquals(6L, item.donationId());
        assertEquals(6L, item.ngoId());
        assertEquals(9L, item.volunteerId());
        assertEquals(new BigDecimal("41.20"), item.quantityKg());
        assertEquals(DeliveryStatus.DELIVERED, item.status());

        verify(deliveries).findByPickupRequest_Volunteer_UserIdAndStatusIn(eq(9L), statusCaptor.capture(), any());
        Collection<DeliveryStatus> used = statusCaptor.getValue();
        assertEquals(3, used.size());
        assertTrue(used.containsAll(List.of(DeliveryStatus.DELIVERED, DeliveryStatus.RECEIPT_CONFIRMED, DeliveryStatus.FAILED)));
    }

    @Test
    void ngoHistoryUsesTheNgoQuery() {
        PagedResult<HistoryItemResponse> res = service.forNgo(6L, null, 0, 20);

        assertEquals(1, res.content().size());
        verify(deliveries).findByPickupRequest_Ngo_UserIdAndStatusIn(eq(6L), any(), any());
        verify(deliveries, never()).findByPickupRequest_Volunteer_UserIdAndStatusIn(any(), any(), any());
    }

    @Test
    void statusFilterNarrowsToOneStatus() {
        service.forVolunteer(9L, DeliveryStatus.FAILED, 0, 20);

        verify(deliveries).findByPickupRequest_Volunteer_UserIdAndStatusIn(eq(9L), statusCaptor.capture(), any());
        assertEquals(List.of(DeliveryStatus.FAILED), List.copyOf(statusCaptor.getValue()));
    }

    @Test
    void unfinishedStatusFilterIsRejected() {
        assertThrows(BadRequestException.class, () -> service.forNgo(6L, DeliveryStatus.IN_TRANSIT, 0, 20));
        verify(deliveries, never()).findByPickupRequest_Ngo_UserIdAndStatusIn(any(), any(), any());
    }

    @Test
    void pageSizeIsCappedAtOneHundred() {
        service.forVolunteer(9L, null, 0, 5000);

        verify(deliveries).findByPickupRequest_Volunteer_UserIdAndStatusIn(any(), any(), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void badPageAndSizeFallBackToDefaults() {
        service.forVolunteer(9L, null, -3, 0);

        verify(deliveries).findByPickupRequest_Volunteer_UserIdAndStatusIn(any(), any(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }
}