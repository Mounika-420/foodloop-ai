package com.foodloop.service;

import com.foodloop.dto.ConfirmDeliveryRequest;
import com.foodloop.dto.CreatePickupRequest;
import com.foodloop.dto.DeliveryStatusRequest;
import com.foodloop.dto.PickupResponse;
import com.foodloop.entity.Delivery;
import com.foodloop.entity.Donation;
import com.foodloop.entity.Location;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.InvalidStatusTransitionException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DeliveryRepository;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.PickupRequestRepository;
import com.foodloop.repository.VolunteerProfileRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PickupService {

    private static final List<PickupStatus> VOLUNTEER_ACTIVE =
            List.of(PickupStatus.ASSIGNED, PickupStatus.VOLUNTEER_ACCEPTED, PickupStatus.PICKED_UP);

    private final PickupRequestRepository pickups;
    private final DeliveryRepository deliveries;
    private final DonationRepository donations;
    private final VolunteerProfileRepository volunteers;
    private final AuditService auditService;

    public PickupService(PickupRequestRepository pickups,
                         DeliveryRepository deliveries,
                         DonationRepository donations,
                         VolunteerProfileRepository volunteers,
                         AuditService auditService) {
        this.pickups = pickups;
        this.deliveries = deliveries;
        this.donations = donations;
        this.volunteers = volunteers;
        this.auditService = auditService;
    }

    /** NGO asks for a volunteer to collect a donation it has accepted. */
    @Transactional
    public PickupResponse create(Long ngoId, CreatePickupRequest req) {
        Donation d = donations.findById(req.donationId())
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (d.getAcceptedNgo() == null || !d.getAcceptedNgo().getUserId().equals(ngoId)) {
            throw new ResourceNotFoundException("Donation not found");
        }
        if (d.getStatus() != DonationStatus.ACCEPTED) {
            throw new ConflictException("A pickup can only be requested for an ACCEPTED donation (now "
                    + d.getStatus() + ")");
        }
        if (pickups.existsByDonation_Id(d.getId())) {
            throw new ConflictException("A pickup was already requested for this donation");
        }
        LocalDateTime at = req.scheduledPickupAt();
        if (!at.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Scheduled pickup time must be in the future");
        }
        if (at.isBefore(d.getPickupWindowStart()) || at.isAfter(d.getPickupWindowEnd())) {
            throw new BadRequestException("Scheduled pickup must be inside the donor's pickup window ("
                    + d.getPickupWindowStart() + " to " + d.getPickupWindowEnd() + ")");
        }
        PickupRequest p = new PickupRequest();
        p.setDonation(d);
        p.setNgo(d.getAcceptedNgo());
        p.setScheduledPickupAt(at);
        p.setStatus(PickupStatus.PENDING_ASSIGNMENT);
        pickups.save(p);
        auditService.log(ngoId, "CREATE_PICKUP", "PICKUP_REQUEST", p.getId(), null, Map.of("donationId", d.getId()));
        return toResponse(p, null, null);
    }

    @Transactional(readOnly = true)
    public List<PickupResponse> mineForNgo(Long ngoId) {
        List<PickupResponse> out = new ArrayList<>();
        for (PickupRequest p : pickups.findByNgo_UserIdOrderByCreatedAtDesc(ngoId)) {
            out.add(toResponse(p, null, null));
        }
        return out;
    }

    /** Open pickups inside the volunteer's service radius. */
    @Transactional(readOnly = true)
    public List<PickupResponse> available(Long volunteerId) {
        VolunteerProfile v = volunteer(volunteerId);
        List<PickupResponse> out = new ArrayList<>();
        if (v.getAvailabilityStatus() != Availability.AVAILABLE) {
            return out;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PickupRequest p : pickups.findByStatusOrderByCreatedAtAsc(PickupStatus.PENDING_ASSIGNMENT)) {
            Donation d = p.getDonation();
            if (d.getStatus() != DonationStatus.ACCEPTED || !d.getExpiresAt().isAfter(now)) {
                continue;
            }
            double km = distanceKm(v, d);
            if (km <= v.getServiceRadiusKm().doubleValue()) {
                out.add(toResponse(p, null, round1(km)));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<PickupResponse> assigned(Long volunteerId) {
        List<PickupResponse> out = new ArrayList<>();
        for (PickupRequest p : pickups.findByVolunteer_UserIdAndStatusInOrderByScheduledPickupAtAsc(
                volunteerId, VOLUNTEER_ACTIVE)) {
            out.add(toResponse(p, null, null));
        }
        return out;
    }

    @Transactional
    public PickupResponse accept(Long volunteerId, Long pickupId) {
        PickupRequest p = pickups.findById(pickupId)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup not found"));
        VolunteerProfile v = volunteer(volunteerId);
        if (p.getStatus() != PickupStatus.PENDING_ASSIGNMENT) {
            throw new ConflictException("This pickup is no longer open (" + p.getStatus() + ")");
        }
        if (v.getAvailabilityStatus() != Availability.AVAILABLE) {
            throw new ConflictException("Set your availability to AVAILABLE before accepting tasks");
        }
        Donation d = p.getDonation();
        if (d.getStatus() != DonationStatus.ACCEPTED) {
            throw new ConflictException("This donation is no longer open for pickup (" + d.getStatus() + ")");
        }
        if (!d.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ConflictException("This donation has already expired");
        }
        double km = distanceKm(v, d);
        if (km > v.getServiceRadiusKm().doubleValue()) {
            throw new ConflictException("This pickup is " + round1(km) + " km away, outside your service radius of "
                    + v.getServiceRadiusKm() + " km");
        }
        if (!d.getStatus().canTransitionTo(DonationStatus.VOLUNTEER_ASSIGNED)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move donation from " + d.getStatus() + " to VOLUNTEER_ASSIGNED");
        }
        d.setStatus(DonationStatus.VOLUNTEER_ASSIGNED);
        try {
            donations.saveAndFlush(d);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Another volunteer accepted this pickup first");
        }
        p.setVolunteer(v);
        p.setStatus(PickupStatus.VOLUNTEER_ACCEPTED);
        p.setAssignedAt(LocalDateTime.now());
        pickups.save(p);

        Delivery del = new Delivery();
        del.setPickupRequest(p);
        del.setStatus(DeliveryStatus.NOT_STARTED);
        deliveries.save(del);

        auditService.log(volunteerId, "ACCEPT_PICKUP", "PICKUP_REQUEST", p.getId(), null,
                Map.of("donationId", d.getId()));
        return toResponse(p, del, round1(km));
    }

    /** Forward-only: NOT_STARTED -> PICKED_UP -> IN_TRANSIT -> DELIVERED. */
    @Transactional
    public PickupResponse updateStatus(Long volunteerId, Long deliveryId, DeliveryStatusRequest req) {
        Delivery del = deliveries.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
        PickupRequest p = del.getPickupRequest();
        if (p.getVolunteer() == null || !p.getVolunteer().getUserId().equals(volunteerId)) {
            throw new ResourceNotFoundException("Delivery not found");
        }
        if ((req.lat() == null) != (req.lng() == null)) {
            throw new BadRequestException("Send both lat and lng, or neither");
        }
        DeliveryStatus cur = del.getStatus();
        DeliveryStatus next = req.status();
        DonationStatus target;
        if (next == DeliveryStatus.PICKED_UP) {
            target = DonationStatus.PICKED_UP;
        } else if (next == DeliveryStatus.IN_TRANSIT) {
            target = DonationStatus.IN_TRANSIT;
        } else if (next == DeliveryStatus.DELIVERED) {
            target = DonationStatus.DELIVERED;
        } else {
            throw new BadRequestException("Volunteers can only set PICKED_UP, IN_TRANSIT or DELIVERED");
        }
        DeliveryStatus expected = cur == DeliveryStatus.NOT_STARTED ? DeliveryStatus.PICKED_UP
                : cur == DeliveryStatus.PICKED_UP ? DeliveryStatus.IN_TRANSIT
                : cur == DeliveryStatus.IN_TRANSIT ? DeliveryStatus.DELIVERED
                : null;
        if (expected == null) {
            throw new ConflictException("This delivery is already " + cur);
        }
        if (next != expected) {
            throw new ConflictException("Delivery is " + cur + "; the next allowed status is " + expected);
        }
        Donation d = p.getDonation();
        if (!d.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move donation from " + d.getStatus() + " to " + target);
        }
        d.setStatus(target);
        try {
            donations.saveAndFlush(d);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("The donation was changed by someone else, please retry");
        }
        LocalDateTime now = LocalDateTime.now();
        del.setStatus(next);
        if (req.lat() != null) {
            del.setLastLatitude(req.lat());
            del.setLastLongitude(req.lng());
        }
        if (next == DeliveryStatus.PICKED_UP) {
            del.setPickedUpAt(now);
            p.setStatus(PickupStatus.PICKED_UP);
            pickups.save(p);
        } else if (next == DeliveryStatus.DELIVERED) {
            del.setDeliveredAt(now);
        }
        deliveries.save(del);
        auditService.log(volunteerId, "UPDATE_DELIVERY", "DELIVERY", del.getId(), null, Map.of("status", next.name()));
        return toResponse(p, del, null);
    }

    /** NGO confirms it received the food; the donation becomes COMPLETED. */
    @Transactional
    public PickupResponse confirm(Long ngoId, Long deliveryId, ConfirmDeliveryRequest req) {
        Delivery del = deliveries.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
        PickupRequest p = del.getPickupRequest();
        if (!p.getNgo().getUserId().equals(ngoId)) {
            throw new ResourceNotFoundException("Delivery not found");
        }
        if (del.getStatus() != DeliveryStatus.DELIVERED) {
            throw new ConflictException("Receipt can be confirmed only after DELIVERED (delivery is "
                    + del.getStatus() + ")");
        }
        Donation d = p.getDonation();
        if (req.quantityReceivedKg().compareTo(d.getQuantityKg()) > 0) {
            throw new BadRequestException("Received quantity cannot exceed the donated " + d.getQuantityKg() + " kg");
        }
        if (!d.getStatus().canTransitionTo(DonationStatus.COMPLETED)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move donation from " + d.getStatus() + " to COMPLETED");
        }
        d.setStatus(DonationStatus.COMPLETED);
        try {
            donations.saveAndFlush(d);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("The donation was changed by someone else, please retry");
        }
        del.setStatus(DeliveryStatus.RECEIPT_CONFIRMED);
        del.setReceiptConfirmedAt(LocalDateTime.now());
        del.setQuantityReceivedKg(req.quantityReceivedKg());
        del.setConfirmationNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        deliveries.save(del);

        VolunteerProfile v = p.getVolunteer();
        if (v != null) {
            v.setTotalDeliveries((v.getTotalDeliveries() == null ? 0 : v.getTotalDeliveries()) + 1);
            volunteers.save(v);
        }
        auditService.log(ngoId, "CONFIRM_DELIVERY", "DELIVERY", del.getId(), null,
                Map.of("donationId", d.getId(), "quantityReceivedKg", req.quantityReceivedKg()));
        return toResponse(p, del, null);
    }

    private VolunteerProfile volunteer(Long id) {
        return volunteers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Volunteer profile not found"));
    }

    private PickupResponse toResponse(PickupRequest p, Delivery known, Double distanceKm) {
        Delivery del = known != null ? known : deliveries.findByPickupRequest_Id(p.getId()).orElse(null);
        Donation d = p.getDonation();
        Location loc = d.getPickupLocation();
        return new PickupResponse(
                p.getId(), d.getId(),
                d.getCategory() == null ? null : d.getCategory().getName(),
                d.getQuantityKg(),
                p.getNgo().getUserId(), p.getNgo().getOrganizationName(),
                p.getVolunteer() == null ? null : p.getVolunteer().getUserId(),
                p.getScheduledPickupAt(), p.getStatus(), p.getAssignedAt(), p.getCreatedAt(),
                d.getPickupWindowStart(), d.getPickupWindowEnd(), d.getExpiresAt(),
                loc == null ? null : loc.getLatitude(), loc == null ? null : loc.getLongitude(),
                distanceKm,
                del == null ? null : del.getId(), del == null ? null : del.getStatus());
    }

    private static double distanceKm(VolunteerProfile v, Donation d) {
        Location a = v.getBaseLocation();
        Location b = d.getPickupLocation();
        return haversineKm(a.getLatitude().doubleValue(), a.getLongitude().doubleValue(),
                b.getLatitude().doubleValue(), b.getLongitude().doubleValue());
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(x)));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}