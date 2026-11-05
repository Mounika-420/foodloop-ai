package com.foodloop.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodloop.entity.enums.DonationStatus;
import org.junit.jupiter.api.Test;

class DonationStatusTest {
    @Test
    void happyPathIsAllowed() {
        assertTrue(DonationStatus.AVAILABLE.canTransitionTo(DonationStatus.MATCHED));
        assertTrue(DonationStatus.MATCHED.canTransitionTo(DonationStatus.ACCEPTED));
        assertTrue(DonationStatus.ACCEPTED.canTransitionTo(DonationStatus.VOLUNTEER_ASSIGNED));
        assertTrue(DonationStatus.VOLUNTEER_ASSIGNED.canTransitionTo(DonationStatus.PICKED_UP));
        assertTrue(DonationStatus.PICKED_UP.canTransitionTo(DonationStatus.IN_TRANSIT));
        assertTrue(DonationStatus.IN_TRANSIT.canTransitionTo(DonationStatus.DELIVERED));
        assertTrue(DonationStatus.DELIVERED.canTransitionTo(DonationStatus.COMPLETED));
    }

    @Test
    void cannotSkipStates() {
        assertFalse(DonationStatus.AVAILABLE.canTransitionTo(DonationStatus.COMPLETED));
        assertFalse(DonationStatus.ACCEPTED.canTransitionTo(DonationStatus.DELIVERED));
    }

    @Test
    void cancelOnlyBeforePickup() {
        assertTrue(DonationStatus.MATCHED.isCancellable());
        assertTrue(DonationStatus.VOLUNTEER_ASSIGNED.isCancellable());
        assertFalse(DonationStatus.PICKED_UP.isCancellable());
        assertFalse(DonationStatus.IN_TRANSIT.isCancellable());
    }

    @Test
    void terminalStatesHaveNoNext() {
        assertTrue(DonationStatus.COMPLETED.isTerminal());
        assertTrue(DonationStatus.CANCELLED.isTerminal());
        assertTrue(DonationStatus.EXPIRED.isTerminal());
        assertFalse(DonationStatus.MATCHED.isTerminal());
    }
}
