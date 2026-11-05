package com.foodloop.service;

import com.foodloop.dto.DonationRequest;
import com.foodloop.dto.DonationResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.Donation;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.FoodItem;
import com.foodloop.entity.Location;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.Unit;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.mapper.DonationMapper;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.FoodCategoryRepository;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.LocationRepository;
import com.foodloop.util.DonationCodeGenerator;
import com.foodloop.util.UnitConverter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class DonationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DonationService.class);
    private static final Set<String> SORTABLE = Set.of("createdAt", "expiresAt", "quantityKg", "foodName");

    private final DonationRepository donationRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodItemRepository foodItemRepository;
    private final LocationRepository locationRepository;
    private final AuditService auditService;
    private final MatchingService matchingService;

    public DonationService(DonationRepository donationRepository,
                           DonorProfileRepository donorProfileRepository,
                           FoodCategoryRepository foodCategoryRepository,
                           FoodItemRepository foodItemRepository,
                           LocationRepository locationRepository,
                           AuditService auditService,
                           MatchingService matchingService) {
        this.donationRepository = donationRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.foodItemRepository = foodItemRepository;
        this.locationRepository = locationRepository;
        this.auditService = auditService;
        this.matchingService = matchingService;
    }

    @Transactional
    public DonationResponse create(Long donorId, DonationRequest r) {
        DonorProfile donor = requireApprovedDonor(donorId);
        Donation d = new Donation();
        d.setDonor(donor);
        apply(d, r, donor, true);
        d.setStatus(DonationStatus.AVAILABLE);
        d.setDonationCode(uniqueCode());
        Donation saved = donationRepository.save(d);
        auditService.log(donorId, "CREATE_DONATION", "DONATION", saved.getId(), null, null);
        try {
            matchingService.matchDonation(saved);
        } catch (RuntimeException ex) {
            log.warn("Matching failed for donation {}: {}", saved.getId(), ex.getMessage());
        }
        return DonationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResult<DonationResponse> mine(Long donorId, DonationStatus status, String q, Pageable pageable) {
        List<DonationStatus> statuses = status == null ? List.copyOf(EnumSet.allOf(DonationStatus.class)) : List.of(status);
        return PagedResult.from(donationRepository
                .searchMine(donorId, statuses, clean(q), safe(pageable))
                .map(DonationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PagedResult<DonationResponse> available(String category, String q, Pageable pageable) {
        return PagedResult.from(donationRepository
                .searchAvailable(DonationStatus.AVAILABLE, LocalDateTime.now(), clean(category), clean(q), safe(pageable))
                .map(DonationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public DonationResponse get(Long userId, String role, Long id) {
        Donation d = donationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        boolean owner = d.getDonor().getUserId().equals(userId);
        boolean admin = "ADMIN".equals(role);
        boolean ngo = "NGO".equals(role) && (d.getStatus() == DonationStatus.AVAILABLE || matchingService.hasOffer(d.getId(), userId)
                || (d.getAcceptedNgo() != null && d.getAcceptedNgo().getUserId().equals(userId)));
        if (!(owner || admin || ngo)) {
            throw new AccessDeniedException("Not allowed to view this donation");
        }
        return DonationMapper.toResponse(d);
    }

    @Transactional
    public DonationResponse update(Long donorId, Long id, DonationRequest r) {
        Donation d = findOwned(donorId, id);
        if (d.getStatus() != DonationStatus.AVAILABLE) {
            throw new ConflictException("Only AVAILABLE donations can be edited");
        }
        apply(d, r, d.getDonor(), false);
        Donation saved = donationRepository.save(d);
        auditService.log(donorId, "UPDATE_DONATION", "DONATION", saved.getId(), null, null);
        return DonationMapper.toResponse(saved);
    }

    @Transactional
    public DonationResponse cancel(Long donorId, Long id) {
        Donation d = findOwned(donorId, id);
        if (!d.getStatus().isCancellable()) {
            throw new ConflictException("A donation that is " + d.getStatus() + " can no longer be cancelled");
        }
        d.setStatus(DonationStatus.CANCELLED);
        Donation saved = donationRepository.save(d);
        auditService.log(donorId, "CANCEL_DONATION", "DONATION", saved.getId(), null, null);
        return DonationMapper.toResponse(saved);
    }

    private DonorProfile requireApprovedDonor(Long donorId) {
        DonorProfile donor = donorProfileRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found"));
        String vs = donor.getVerificationStatus() == null ? "" : donor.getVerificationStatus().name();
        if (vs.equals("PENDING") || vs.equals("REJECTED") || vs.equals("SUSPENDED")) {
            throw new AccessDeniedException("Your donor account is " + vs + ". An admin must approve it before you can donate.");
        }
        return donor;
    }

    private Donation findOwned(Long donorId, Long id) {
        Donation d = donationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (!d.getDonor().getUserId().equals(donorId)) {
            throw new ResourceNotFoundException("Donation not found");
        }
        return d;
    }

    private void apply(Donation d, DonationRequest r, DonorProfile donor, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(r.safetyDeclared())) {
            throw new BadRequestException("You must confirm the food is safe to consume (safetyDeclared)");
        }
        if (!r.expiresAt().isAfter(now)) {
            throw new BadRequestException("Expiry time must be in the future");
        }
        if (!r.expiresAt().isAfter(r.preparedAt())) {
            throw new BadRequestException("Expiry time must be after the preparation time");
        }
        if (!r.pickupWindowEnd().isAfter(r.pickupWindowStart())) {
            throw new BadRequestException("Pickup window end must be after its start");
        }
        if (!r.pickupWindowEnd().isAfter(now)) {
            throw new BadRequestException("Pickup window must end in the future");
        }
        if (r.pickupWindowEnd().isAfter(r.expiresAt())) {
            throw new BadRequestException("Pickup window must end before the food expires");
        }

        FoodCategory category = foodCategoryRepository.findById(r.categoryId())
                .orElseThrow(() -> new BadRequestException("Unknown food category"));

        FoodItem item = null;
        if (r.foodItemId() != null) {
            item = foodItemRepository.findById(r.foodItemId())
                    .orElseThrow(() -> new BadRequestException("Unknown food item"));
            if (!item.getCategory().getId().equals(category.getId())) {
                throw new BadRequestException("The food item does not belong to the selected category");
            }
        }

        BigDecimal kgPerUnit = item == null ? null : item.getKgPerUnit();
        if ((r.unit() == Unit.PORTIONS || r.unit() == Unit.PIECES) && kgPerUnit == null) {
            throw new BadRequestException(
                    "foodItemId is required when the unit is PORTIONS or PIECES, so the quantity can be converted to kg");
        }
        BigDecimal quantity = r.quantity().setScale(2, RoundingMode.HALF_UP);
        BigDecimal quantityKg = UnitConverter.toKg(r.unit(), quantity, kgPerUnit);
        if (quantityKg.signum() <= 0) {
            throw new BadRequestException("Quantity is too small");
        }

        d.setCategory(category);
        d.setFoodItem(item);
        d.setFoodName(r.foodName().trim());
        d.setQuantity(quantity);
        d.setUnit(r.unit());
        d.setQuantityKg(quantityKg);
        d.setPreparedAt(r.preparedAt());
        d.setExpiresAt(r.expiresAt());
        d.setStorageCondition(r.storageCondition());
        d.setAllergens(r.allergens());
        d.setVegetarian(r.vegetarian() == null || r.vegetarian());
        d.setDescription(r.description());
        d.setPickupWindowStart(r.pickupWindowStart());
        d.setPickupWindowEnd(r.pickupWindowEnd());
        d.setSafetyDeclared(true);

        if (r.pickupLocation() != null) {
            DonationRequest.LocationInput in = r.pickupLocation();
            Location loc = new Location();
            loc.setAddressLine(in.addressLine().trim());
            loc.setArea(in.area());
            loc.setCity(in.city().trim());
            loc.setState(in.state().trim());
            loc.setPincode(in.pincode());
            loc.setLatitude(in.latitude());
            loc.setLongitude(in.longitude());
            d.setPickupLocation(locationRepository.save(loc));
        } else if (creating) {
            d.setPickupLocation(donor.getLocation());
        }
    }

    private String uniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = DonationCodeGenerator.generate();
            if (donationRepository.findByDonationCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique donation code");
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static Pageable safe(Pageable p) {
        Sort sort = Sort.by(p.getSort().stream().filter(o -> SORTABLE.contains(o.getProperty())).toList());
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return PageRequest.of(p.getPageNumber(), Math.min(p.getPageSize(), MAX_PAGE_SIZE), sort);
    }
}