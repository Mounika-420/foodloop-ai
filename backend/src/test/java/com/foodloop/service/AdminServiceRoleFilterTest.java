package com.foodloop.service;

import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceRoleFilterTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AdminService service;

    private final Pageable page = PageRequest.of(0, 20);

    @Test
    void unknownRoleIsRejectedWithTheAllowedValues() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.users("BOGUS", null, "", page));

        assertTrue(ex.getMessage().contains("ADMIN, DONOR, NGO, VOLUNTEER"));
        verify(userRepository, never()).search(anyString(), any(), any(), any());
    }

    @Test
    void blankRoleMeansNoFilter() {
        when(userRepository.search(eq(""), any(), any(), any())).thenReturn(Page.empty());

        service.users("", null, "", page);

        verify(userRepository).search(eq(""), any(), any(), any());
    }

    @Test
    void lowerCaseRoleIsAccepted() {
        when(userRepository.search(eq("NGO"), any(), any(), any())).thenReturn(Page.empty());

        service.users(" ngo ", null, "", page);

        verify(userRepository).search(eq("NGO"), any(), any(), any());
    }
}