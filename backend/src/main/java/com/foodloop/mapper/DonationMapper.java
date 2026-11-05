package com.foodloop.mapper;

import com.foodloop.dto.DonationResponse;
import com.foodloop.dto.DonationResponse.CategoryRef;
import com.foodloop.dto.DonationResponse.LocationResponse;
import com.foodloop.entity.Donation;
import com.foodloop.entity.Location;

public final class DonationMapper {

    private DonationMapper() {
    }

    public static DonationResponse toResponse(Donation d) {
        Location l = d.getPickupLocation();
        LocationResponse loc = l == null ? null : new LocationResponse(l.getId(), l.getAddressLine(), l.getArea(),
                l.getCity(), l.getState(), l.getPincode(), l.getLatitude(), l.getLongitude());
        return new DonationResponse(
                d.getId(),
                d.getDonationCode(),
                new CategoryRef(d.getCategory().getId(), d.getCategory().getName()),
                d.getFoodName(),
                d.getQuantity(),
                d.getUnit(),
                d.getQuantityKg(),
                d.getPreparedAt(),
                d.getExpiresAt(),
                d.getStorageCondition(),
                d.getAllergens(),
                d.isVegetarian(),
                d.getDescription(),
                loc,
                d.getPickupWindowStart(),
                d.getPickupWindowEnd(),
                d.isSafetyDeclared(),
                d.getStatus(),
                d.getDonor().getUserId(),
                d.getCreatedAt());
    }
}