package com.foodloop.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Donation lifecycle. The legal transitions here are the single source of truth
 * (see documentation/design/01_requirements_and_architecture.md, section 7).
 */
public enum DonationStatus {
    AVAILABLE, MATCHED, ACCEPTED, VOLUNTEER_ASSIGNED, PICKED_UP, IN_TRANSIT,
    DELIVERED, COMPLETED, CANCELLED, EXPIRED, REJECTED_ALL;

    public Set<DonationStatus> allowedNext() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(MATCHED, CANCELLED, EXPIRED);
            case MATCHED -> EnumSet.of(ACCEPTED, REJECTED_ALL, CANCELLED, EXPIRED);
            case ACCEPTED -> EnumSet.of(VOLUNTEER_ASSIGNED, CANCELLED);
            case VOLUNTEER_ASSIGNED -> EnumSet.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> EnumSet.of(IN_TRANSIT);
            case IN_TRANSIT -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(COMPLETED);
            case REJECTED_ALL -> EnumSet.of(AVAILABLE, EXPIRED);
            case COMPLETED, CANCELLED, EXPIRED -> EnumSet.noneOf(DonationStatus.class);
        };
    }

    public boolean canTransitionTo(DonationStatus next) {
        return allowedNext().contains(next);
    }

    /** Donor may cancel only before the food is picked up. */
    public boolean isCancellable() {
        return canTransitionTo(CANCELLED);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
