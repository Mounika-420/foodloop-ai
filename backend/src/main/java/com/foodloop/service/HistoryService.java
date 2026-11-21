package com.foodloop.service;

import com.foodloop.dto.HistoryItemResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.Delivery;
import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.DeliveryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class HistoryService {

    static final Set<DeliveryStatus> FINISHED =
            EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.RECEIPT_CONFIRMED, DeliveryStatus.FAILED);
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final DeliveryRepository deliveries;

    public HistoryService(DeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public PagedResult<HistoryItemResponse> forVolunteer(Long volunteerId, DeliveryStatus status, int page, int size) {
        Page<Delivery> result = deliveries.findByPickupRequest_Volunteer_UserIdAndStatusIn(
                volunteerId, statuses(status), pageable(page, size));
        return PagedResult.from(result.map(this::toItem));
    }

    @Transactional(readOnly = true)
    public PagedResult<HistoryItemResponse> forNgo(Long ngoId, DeliveryStatus status, int page, int size) {
        Page<Delivery> result = deliveries.findByPickupRequest_Ngo_UserIdAndStatusIn(
                ngoId, statuses(status), pageable(page, size));
        return PagedResult.from(result.map(this::toItem));
    }

    private Set<DeliveryStatus> statuses(DeliveryStatus filter) {
        if (filter == null) {
            return FINISHED;
        }
        if (!FINISHED.contains(filter)) {
            throw new BadRequestException("History only includes finished deliveries: " + FINISHED);
        }
        return EnumSet.of(filter);
    }

    private Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private HistoryItemResponse toItem(Delivery d) {
        PickupRequest p = d.getPickupRequest();
        var donation = p.getDonation();
        return new HistoryItemResponse(
                d.getId(),
                p.getId(),
                donation.getId(),
                donation.getCategory() == null ? null : donation.getCategory().getName(),
                donation.getQuantityKg(),
                p.getNgo().getUserId(),
                p.getVolunteer() == null ? null : p.getVolunteer().getUserId(),
                d.getStatus(),
                d.getPickedUpAt(),
                d.getDeliveredAt(),
                d.getReceiptConfirmedAt(),
                d.getQuantityReceivedKg(),
                d.getFailureReason());
    }
}