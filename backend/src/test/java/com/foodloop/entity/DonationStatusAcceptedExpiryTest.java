package com.foodloop.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodloop.entity.enums.DonationStatus;
import org.junit.jupiter.api.Test;

class DonationStatusAcceptedExpiryTest {
    @Test
    void acceptedDonationCanExpire() {
        assertTrue(DonationStatus.ACCEPTED.canTransitionTo(DonationStatus.EXPIRED));
    }

    @Test
    void onceAVolunteerHasItTheDonationCannotExpire() {
        assertFalse(DonationStatus.VOLUNTEER_ASSIGNED.canTransitionTo(DonationStatus.EXPIRED));
        assertFalse(DonationStatus.PICKED_UP.canTransitionTo(DonationStatus.EXPIRED));
        assertFalse(DonationStatus.IN_TRANSIT.canTransitionTo(DonationStatus.EXPIRED));
    }

    @Test
    void acceptedDonationCanStillBeAssignedOrCancelled() {
        assertTrue(DonationStatus.ACCEPTED.canTransitionTo(DonationStatus.VOLUNTEER_ASSIGNED));
        assertTrue(DonationStatus.ACCEPTED.canTransitionTo(DonationStatus.CANCELLED));
    }
}