package com.foodloop.service;

import com.foodloop.dto.AcceptedCategoriesResponse;
import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.NgoAcceptedCategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NgoAcceptedCategoryServiceTest {

    @Mock
    NgoAcceptedCategoryRepository repository;
    @Mock
    AuditService auditService;

    NgoAcceptedCategoryService service;

    @BeforeEach
    void setUp() {
        service = new NgoAcceptedCategoryService(repository, auditService);
    }

    @Test
    void savesSortedUniqueIds() {
        when(repository.countExisting(any())).thenReturn(2);

        AcceptedCategoriesResponse r = service.replace(6L, List.of(4, 1, 4));

        verify(repository).replace(eq(6L), eq(Set.of(1, 4)));
        assertEquals(List.of(1, 4), r.categoryIds());
        assertFalse(r.acceptsAll());
    }

    @Test
    void emptyListMeansAcceptsEverything() {
        AcceptedCategoriesResponse r = service.replace(6L, List.of());

        verify(repository).replace(eq(6L), eq(Set.of()));
        assertTrue(r.acceptsAll());
    }

    @Test
    void unknownCategoryIsRejectedAndNothingIsSaved() {
        when(repository.countExisting(any())).thenReturn(1);

        assertThrows(BadRequestException.class, () -> service.replace(6L, List.of(1, 999)));
        verify(repository, never()).replace(any(), any());
    }

    @Test
    void moreThanTwentyCategoriesAreRejected() {
        List<Integer> many = new ArrayList<>(IntStream.rangeClosed(1, 21).boxed().toList());

        assertThrows(BadRequestException.class, () -> service.replace(6L, many));
        verify(repository, never()).replace(any(), any());
    }

    @Test
    void nullEntryIsRejected() {
        assertThrows(BadRequestException.class, () -> service.replace(6L, Arrays.asList(1, null)));
        verify(repository, never()).replace(any(), any());
    }

    @Test
    void changeIsWrittenToTheAuditLog() {
        when(repository.countExisting(any())).thenReturn(1);

        service.replace(6L, List.of(3));

        verify(auditService).log(eq(6L), eq("UPDATE_ACCEPTED_CATEGORIES"), eq("NGO_PROFILE"), eq(6L), any(), any());
    }

    @Test
    void nothingSavedMeansAcceptsAll() {
        when(repository.find(6L)).thenReturn(Set.of());

        assertTrue(service.get(6L).acceptsAll());
    }
}