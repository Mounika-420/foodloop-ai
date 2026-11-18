package com.foodloop.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingServiceAcceptedCategoriesTest {

    @Test
    void ngoWithoutAListAcceptsEverything() {
        assertTrue(MatchingService.acceptsCategory(Map.of(), 6L, 1));
    }

    @Test
    void listedCategoryIsAccepted() {
        assertTrue(MatchingService.acceptsCategory(Map.of(8L, Set.of(2, 4)), 8L, 4));
    }

    @Test
    void unlistedCategoryIsRejected() {
        assertFalse(MatchingService.acceptsCategory(Map.of(8L, Set.of(2, 4)), 8L, 1));
    }

    @Test
    void otherNgosAreNotAffectedByOneNgosList() {
        assertTrue(MatchingService.acceptsCategory(Map.of(8L, Set.of(2, 4)), 6L, 1));
    }
}