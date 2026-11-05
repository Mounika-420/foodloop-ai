package com.foodloop.service;

import com.foodloop.dto.DonationRequest;
import com.foodloop.dto.DonationResponse;
import com.foodloop.entity.Donation;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.FoodItem;
import com.foodloop.entity.Location;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.StorageCondition;
import com.foodloop.entity.enums.Unit;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.FoodCategoryRepository;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DonationServiceTest {

    @Mock DonationRepository donationRepository;
    @Mock DonorProfileRepository donorProfileRepository;
    @Mock FoodCategoryRepository foodCategoryRepository;
    @Mock FoodItemRepository foodItemRepository;
    @Mock LocationRepository locationRepository;
    @Mock AuditService auditService;
    @Mock MatchingService matchingService;

    @InjectMocks DonationService service;

    private DonorProfile donor;
    private FoodCategory category;

    @BeforeEach
    void setUp() {
        Location loc = new Location();
        loc.setId(1L);
        loc.setCity("Chennai");

        donor = new DonorProfile();
        donor.setUserId(2L);
        donor.setLocation(loc);

        category = new FoodCategory();
        category.setId(1);
        category.setName("Cooked Meals");

        when(donorProfileRepository.findById(2L)).thenReturn(Optional.of(donor));
        when(foodCategoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> {
            Donation d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(99L);
            }
            return d;
        });
        when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));
    }

    private DonationRequest req(Unit unit, String qty, Integer foodItemId, LocalDateTime prepared,
                                LocalDateTime expires, boolean safety) {
        LocalDateTime now = LocalDateTime.now();
        return new DonationRequest(1, foodItemId, "Veg Biryani", new BigDecimal(qty), unit, prepared, expires,
                StorageCondition.HOT_HELD, null, true, "Fresh", null,
                now.plusHours(1), now.plusHours(3), safety);
    }

    private DonationRequest validKg() {
        LocalDateTime now = LocalDateTime.now();
        return req(Unit.KG, "20", null, now.minusHours(1), now.plusHours(6), true);
    }

    private Donation existing(DonationStatus status) {
        Donation d = new Donation();
        d.setId(5L);
        d.setDonor(donor);
        d.setCategory(category);
        d.setPickupLocation(donor.getLocation());
        d.setStatus(status);
        when(donationRepository.findById(5L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void createSucceedsAndComputesKg() {
        DonationResponse r = service.create(2L, validKg());
        assertEquals(DonationStatus.AVAILABLE, r.status());
        assertEquals(new BigDecimal("20.00"), r.quantityKg());
        assertTrue(r.donationCode().matches("^FL-\\d{4}-\\d{6}$"));
        assertEquals(2L, r.donorId());
        assertEquals(1L, r.pickupLocation().id());
    }

    @Test
    void createConvertsPortionsUsingFoodItem() {
        FoodItem item = new FoodItem();
        item.setId(7);
        item.setCategory(category);
        item.setKgPerUnit(new BigDecimal("0.400"));
        when(foodItemRepository.findById(7)).thenReturn(Optional.of(item));

        LocalDateTime now = LocalDateTime.now();
        DonationResponse r = service.create(2L, req(Unit.PORTIONS, "10", 7, now.minusHours(1), now.plusHours(6), true));
        assertEquals(new BigDecimal("4.00"), r.quantityKg());
    }

    @Test
    void createRejectsPortionsWithoutFoodItem() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BadRequestException.class,
                () -> service.create(2L, req(Unit.PORTIONS, "10", null, now.minusHours(1), now.plusHours(6), true)));
    }

    @Test
    void createRejectsMissingSafetyDeclaration() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BadRequestException.class,
                () -> service.create(2L, req(Unit.KG, "20", null, now.minusHours(1), now.plusHours(6), false)));
    }

    @Test
    void createRejectsExpiryInThePast() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BadRequestException.class,
                () -> service.create(2L, req(Unit.KG, "20", null, now.minusHours(5), now.minusHours(1), true)));
    }

    @Test
    void createRejectsPickupWindowEndingAfterExpiry() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BadRequestException.class,
                () -> service.create(2L, req(Unit.KG, "20", null, now.minusHours(1), now.plusHours(2), true)));
    }

    @Test
    void createRejectsUnknownCategory() {
        when(foodCategoryRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.create(2L, validKg()));
    }

    @Test
    void pendingDonorCannotDonate() {
        donor.setVerificationStatus(VerificationStatus.valueOf("PENDING"));
        assertThrows(AccessDeniedException.class, () -> service.create(2L, validKg()));
    }

    @Test
    void ownerCanViewOtherDonorCannot() {
        existing(DonationStatus.AVAILABLE);
        assertEquals(5L, service.get(2L, "DONOR", 5L).id());
        assertThrows(AccessDeniedException.class, () -> service.get(3L, "DONOR", 5L));
    }

    @Test
    void ngoCanViewOnlyAvailableDonations() {
        existing(DonationStatus.AVAILABLE);
        assertEquals(5L, service.get(6L, "NGO", 5L).id());

        existing(DonationStatus.CANCELLED);
        assertThrows(AccessDeniedException.class, () -> service.get(6L, "NGO", 5L));
    }

    @Test
    void getUnknownDonationIsNotFound() {
        when(donationRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(2L, "DONOR", 404L));
    }

    @Test
    void updateOnlyWhenAvailable() {
        existing(DonationStatus.MATCHED);
        assertThrows(ConflictException.class, () -> service.update(2L, 5L, validKg()));
    }

    @Test
    void updateChangesFields() {
        existing(DonationStatus.AVAILABLE);
        DonationResponse r = service.update(2L, 5L, validKg());
        assertEquals("Veg Biryani", r.foodName());
        assertEquals(new BigDecimal("20.00"), r.quantityKg());
    }

    @Test
    void otherDonorCannotUpdateOrCancel() {
        existing(DonationStatus.AVAILABLE);
        assertThrows(ResourceNotFoundException.class, () -> service.update(3L, 5L, validKg()));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(3L, 5L));
    }

    @Test
    void cancelWorksBeforePickup() {
        existing(DonationStatus.AVAILABLE);
        assertEquals(DonationStatus.CANCELLED, service.cancel(2L, 5L).status());
    }

    @Test
    void cancelFailsAfterPickup() {
        existing(DonationStatus.PICKED_UP);
        assertThrows(ConflictException.class, () -> service.cancel(2L, 5L));
    }
}