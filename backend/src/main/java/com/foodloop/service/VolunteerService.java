package com.foodloop.service;

import com.foodloop.dto.AvailabilityRequest;
import com.foodloop.dto.AvailabilityResponse;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.PickupStatus;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.PickupRequestRepository;
import com.foodloop.repository.VolunteerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VolunteerService {

    private static final List<PickupStatus> OPEN_PICKUPS =
            List.of(PickupStatus.VOLUNTEER_ACCEPTED, PickupStatus.PICKED_UP);

    private static final Set<DonationStatus> ACTIVE_DONATION_STATES =
            EnumSet.of(DonationStatus.VOLUNTEER_ASSIGNED, DonationStatus.PICKED_UP, DonationStatus.IN_TRANSIT);

    private final VolunteerProfileRepository profiles;
    private final PickupRequestRepository pickups;
    private final AuditService auditService;

    public VolunteerService(VolunteerProfileRepository profiles,
                            PickupRequestRepository pickups,
                            AuditService auditService) {
        this.profiles = profiles;
        this.pickups = pickups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse get(Long volunteerId) {
        return new AvailabilityResponse(find(volunteerId).getAvailabilityStatus());
    }

    @Transactional
    public AvailabilityResponse update(Long volunteerId, AvailabilityRequest req) {
        VolunteerProfile profile = find(volunteerId);
        Availability from = profile.getAvailabilityStatus();
        Availability to = req.availability();

        if (from == to) {
            return new AvailabilityResponse(to);
        }
        if (to == Availability.OFFLINE && hasActiveDelivery(volunteerId)) {
            throw new ConflictException("Finish your current delivery before going offline");
        }

        profile.setAvailabilityStatus(to);
        profiles.save(profile);
        auditService.log(volunteerId, "UPDATE_AVAILABILITY", "VOLUNTEER", volunteerId, null,
                Map.of("from", String.valueOf(from), "to", String.valueOf(to)));
        return new AvailabilityResponse(to);
    }

    private VolunteerProfile find(Long volunteerId) {
        return profiles.findById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Volunteer profile not found"));
    }

    private boolean hasActiveDelivery(Long volunteerId) {
        for (var pickup : pickups.findByVolunteer_UserIdAndStatusInOrderByScheduledPickupAtAsc(volunteerId, OPEN_PICKUPS)) {
            if (ACTIVE_DONATION_STATES.contains(pickup.getDonation().getStatus())) {
                return true;
            }
        }
        return false;
    }
}