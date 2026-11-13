package com.foodloop.service;

import com.foodloop.dto.ConfirmDeliveryRequest;
import com.foodloop.dto.CreatePickupRequest;
import com.foodloop.dto.DeliveryStatusRequest;
import com.foodloop.dto.PickupResponse;
import com.foodloop.entity.Delivery;
import com.foodloop.entity.Donation;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.Location;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DeliveryRepository;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.PickupRequestRepository;
import com.foodloop.repository.VolunteerProfileRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PickupServiceTest {

    @Mock PickupRequestRepository pickups;
    @Mock DeliveryRepository deliveries;
    @Mock DonationRepository donations;
    @Mock VolunteerProfileRepository volunteers;
    @Mock AuditService auditService;

    PickupService service;

    @BeforeEach
    void setUp() {
        service = new PickupService(pickups, deliveries, donations, volunteers, auditService);
        when(pickups.save(any(PickupRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(deliveries.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));
        when(donations.saveAndFlush(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(volunteers.save(any(VolunteerProfile.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Location loc(double lat, double lon) {
        Location l = new Location();
        l.setLatitude(BigDecimal.valueOf(lat));
        l.setLongitude(BigDecimal.valueOf(lon));
        return l;
    }

    private NgoProfile ngo(long id) {
        NgoProfile n = new NgoProfile();
        n.setUserId(id);
        n.setOrganizationName("NGO " + id);
        return n;
    }

    private Donation donation(DonationStatus status, NgoProfile ngo) {
        LocalDateTime now = LocalDateTime.now();
        FoodCategory cat = new FoodCategory();
        cat.setId(1);
        cat.setName("Cooked Meals");
        Donation d = new Donation();
        d.setId(4L);
        d.setStatus(status);
        d.setCategory(cat);
        d.setQuantityKg(new BigDecimal("20.00"));
        d.setPickupLocation(loc(11.0, 76.95));
        d.setPickupWindowStart(now.plusHours(1));
        d.setPickupWindowEnd(now.plusHours(4));
        d.setExpiresAt(now.plusHours(10));
        d.setAcceptedNgo(ngo);
        when(donations.findById(4L)).thenReturn(Optional.of(d));
        return d;
    }

    private VolunteerProfile volunteer(long id, String radius, Availability av) {
        VolunteerProfile v = new VolunteerProfile();
        v.setUserId(id);
        v.setServiceRadiusKm(new BigDecimal(radius));
        v.setAvailabilityStatus(av);
        v.setBaseLocation(loc(11.0, 76.95));
        v.setTotalDeliveries(0);
        when(volunteers.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private PickupRequest pickup(long id, Donation d, NgoProfile n, PickupStatus status) {
        PickupRequest p = new PickupRequest();
        p.setId(id);
        p.setDonation(d);
        p.setNgo(n);
        p.setStatus(status);
        p.setScheduledPickupAt(LocalDateTime.now().plusHours(2));
        when(pickups.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Delivery delivery(long id, PickupRequest p, DeliveryStatus status) {
        Delivery del = new Delivery();
        del.setId(id);
        del.setPickupRequest(p);
        del.setStatus(status);
        when(deliveries.findById(id)).thenReturn(Optional.of(del));
        return del;
    }

    @Test
    void createStoresPendingPickupForAcceptedDonation() {
        NgoProfile n = ngo(7);
        donation(DonationStatus.ACCEPTED, n);
        PickupResponse r = service.create(7L, new CreatePickupRequest(4L, LocalDateTime.now().plusHours(2)));
        assertEquals(PickupStatus.PENDING_ASSIGNMENT, r.status());
        assertEquals(7L, r.ngoId());
        assertEquals(4L, r.donationId());
    }

    @Test
    void createByAnotherNgoIsNotFound() {
        donation(DonationStatus.ACCEPTED, ngo(7));
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(6L, new CreatePickupRequest(4L, LocalDateTime.now().plusHours(2))));
    }

    @Test
    void createTwiceIsAConflict() {
        donation(DonationStatus.ACCEPTED, ngo(7));
        when(pickups.existsByDonation_Id(4L)).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.create(7L, new CreatePickupRequest(4L, LocalDateTime.now().plusHours(2))));
    }

    @Test
    void createRequiresAcceptedDonation() {
        donation(DonationStatus.MATCHED, ngo(7));
        assertThrows(ConflictException.class,
                () -> service.create(7L, new CreatePickupRequest(4L, LocalDateTime.now().plusHours(2))));
    }

    @Test
    void createOutsidePickupWindowIsRejected() {
        donation(DonationStatus.ACCEPTED, ngo(7));
        assertThrows(BadRequestException.class,
                () -> service.create(7L, new CreatePickupRequest(4L, LocalDateTime.now().plusHours(6))));
    }

    @Test
    void acceptAssignsVolunteerAndCreatesDelivery() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.ACCEPTED, n), n, PickupStatus.PENDING_ASSIGNMENT);
        volunteer(9, "10.0", Availability.AVAILABLE);

        PickupResponse r = service.accept(9L, 1L);

        assertEquals(PickupStatus.VOLUNTEER_ACCEPTED, p.getStatus());
        assertEquals(DonationStatus.VOLUNTEER_ASSIGNED, p.getDonation().getStatus());
        assertEquals(9L, p.getVolunteer().getUserId());
        ArgumentCaptor<Delivery> cap = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(cap.capture());
        assertEquals(DeliveryStatus.NOT_STARTED, cap.getValue().getStatus());
        assertEquals(DeliveryStatus.NOT_STARTED, r.deliveryStatus());
    }

    @Test
    void acceptWhenAlreadyTakenIsAConflict() {
        NgoProfile n = ngo(7);
        pickup(1, donation(DonationStatus.VOLUNTEER_ASSIGNED, n), n, PickupStatus.VOLUNTEER_ACCEPTED);
        volunteer(10, "10.0", Availability.AVAILABLE);
        assertThrows(ConflictException.class, () -> service.accept(10L, 1L));
    }

    @Test
    void acceptOutsideServiceRadiusIsAConflict() {
        NgoProfile n = ngo(7);
        Donation d = donation(DonationStatus.ACCEPTED, n);
        d.setPickupLocation(loc(11.5, 76.95));
        pickup(1, d, n, PickupStatus.PENDING_ASSIGNMENT);
        volunteer(9, "10.0", Availability.AVAILABLE);
        assertThrows(ConflictException.class, () -> service.accept(9L, 1L));
    }

    @Test
    void offlineVolunteerCannotAccept() {
        NgoProfile n = ngo(7);
        pickup(1, donation(DonationStatus.ACCEPTED, n), n, PickupStatus.PENDING_ASSIGNMENT);
        volunteer(9, "10.0", Availability.OFFLINE);
        assertThrows(ConflictException.class, () -> service.accept(9L, 1L));
    }

    @Test
    void losingTheOptimisticLockRaceIsAConflict() {
        NgoProfile n = ngo(7);
        pickup(1, donation(DonationStatus.ACCEPTED, n), n, PickupStatus.PENDING_ASSIGNMENT);
        volunteer(9, "10.0", Availability.AVAILABLE);
        when(donations.saveAndFlush(any(Donation.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Donation.class, 4L));
        assertThrows(ConflictException.class, () -> service.accept(9L, 1L));
    }

    @Test
    void availableListsOnlyPickupsInsideRadius() {
        NgoProfile n = ngo(7);
        Donation near = donation(DonationStatus.ACCEPTED, n);
        PickupRequest p1 = pickup(1, near, n, PickupStatus.PENDING_ASSIGNMENT);
        Donation far = new Donation();
        far.setId(5L);
        far.setStatus(DonationStatus.ACCEPTED);
        far.setQuantityKg(new BigDecimal("5"));
        far.setPickupLocation(loc(11.6, 76.95));
        far.setPickupWindowStart(LocalDateTime.now().plusHours(1));
        far.setPickupWindowEnd(LocalDateTime.now().plusHours(3));
        far.setExpiresAt(LocalDateTime.now().plusHours(8));
        PickupRequest p2 = pickup(2, far, n, PickupStatus.PENDING_ASSIGNMENT);
        when(pickups.findByStatusOrderByCreatedAtAsc(PickupStatus.PENDING_ASSIGNMENT)).thenReturn(List.of(p1, p2));
        volunteer(9, "10.0", Availability.AVAILABLE);

        List<PickupResponse> r = service.available(9L);

        assertEquals(1, r.size());
        assertEquals(1L, r.get(0).id());
    }

    @Test
    void statusMovesForwardAndUpdatesDonation() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.VOLUNTEER_ASSIGNED, n), n, PickupStatus.VOLUNTEER_ACCEPTED);
        p.setVolunteer(volunteer(9, "10.0", Availability.AVAILABLE));
        Delivery del = delivery(3, p, DeliveryStatus.NOT_STARTED);

        service.updateStatus(9L, 3L, new DeliveryStatusRequest(DeliveryStatus.PICKED_UP, new BigDecimal("11.0"), new BigDecimal("76.95")));

        assertEquals(DeliveryStatus.PICKED_UP, del.getStatus());
        assertEquals(PickupStatus.PICKED_UP, p.getStatus());
        assertEquals(DonationStatus.PICKED_UP, p.getDonation().getStatus());
        assertTrue(del.getPickedUpAt() != null);
    }

    @Test
    void skippingAStatusIsAConflict() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.VOLUNTEER_ASSIGNED, n), n, PickupStatus.VOLUNTEER_ACCEPTED);
        p.setVolunteer(volunteer(9, "10.0", Availability.AVAILABLE));
        delivery(3, p, DeliveryStatus.NOT_STARTED);
        assertThrows(ConflictException.class,
                () -> service.updateStatus(9L, 3L, new DeliveryStatusRequest(DeliveryStatus.DELIVERED, null, null)));
    }

    @Test
    void anotherVolunteerCannotUpdateTheDelivery() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.VOLUNTEER_ASSIGNED, n), n, PickupStatus.VOLUNTEER_ACCEPTED);
        p.setVolunteer(volunteer(9, "10.0", Availability.AVAILABLE));
        delivery(3, p, DeliveryStatus.NOT_STARTED);
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(10L, 3L, new DeliveryStatusRequest(DeliveryStatus.PICKED_UP, null, null)));
    }

    @Test
    void confirmCompletesTheDonationAndCountsTheDelivery() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.DELIVERED, n), n, PickupStatus.PICKED_UP);
        VolunteerProfile v = volunteer(9, "10.0", Availability.AVAILABLE);
        p.setVolunteer(v);
        Delivery del = delivery(3, p, DeliveryStatus.DELIVERED);

        service.confirm(7L, 3L, new ConfirmDeliveryRequest(new BigDecimal("19.50"), "All good"));

        assertEquals(DeliveryStatus.RECEIPT_CONFIRMED, del.getStatus());
        assertEquals(DonationStatus.COMPLETED, p.getDonation().getStatus());
        assertEquals(1, v.getTotalDeliveries());
        assertEquals(new BigDecimal("19.50"), del.getQuantityReceivedKg());
    }

    @Test
    void confirmBeforeDeliveredIsAConflict() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.IN_TRANSIT, n), n, PickupStatus.PICKED_UP);
        delivery(3, p, DeliveryStatus.IN_TRANSIT);
        assertThrows(ConflictException.class,
                () -> service.confirm(7L, 3L, new ConfirmDeliveryRequest(new BigDecimal("10"), null)));
    }

    @Test
    void confirmByAnotherNgoIsNotFound() {
        NgoProfile n = ngo(7);
        PickupRequest p = pickup(1, donation(DonationStatus.DELIVERED, n), n, PickupStatus.PICKED_UP);
        delivery(3, p, DeliveryStatus.DELIVERED);
        assertThrows(ResourceNotFoundException.class,
                () -> service.confirm(6L, 3L, new ConfirmDeliveryRequest(new BigDecimal("10"), null)));
    }
}