package com.foodloop.service;

import com.foodloop.dto.AdminUserResponse;
import com.foodloop.dto.VerificationResponse;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.Role;
import com.foodloop.entity.User;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock DonorProfileRepository donorProfileRepository;
    @Mock NgoProfileRepository ngoProfileRepository;
    @Mock AuditService auditService;

    @InjectMocks AdminService service;

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(ngoProfileRepository.save(any(NgoProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(donorProfileRepository.save(any(DonorProfile.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User user(long id, String roleName, UserStatus status) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@x.test");
        u.setFullName("User " + id);
        u.setRole(role);
        u.setStatus(status);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private NgoProfile ngo(long id, VerificationStatus vs) {
        NgoProfile n = new NgoProfile();
        n.setUserId(id);
        n.setOrganizationName("Org " + id);
        n.setRegistrationNumber("REG-" + id);
        n.setVerificationStatus(vs);
        when(ngoProfileRepository.findById(id)).thenReturn(Optional.of(n));
        return n;
    }

    private DonorProfile donor(long id, VerificationStatus vs) {
        DonorProfile d = new DonorProfile();
        d.setUserId(id);
        d.setVerificationStatus(vs);
        when(donorProfileRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void suspendsAnActiveUser() {
        User u = user(11, "VOLUNTEER", UserStatus.ACTIVE);
        AdminUserResponse r = service.changeStatus(1L, 11L, UserStatus.SUSPENDED, "abuse");
        assertEquals(UserStatus.SUSPENDED, r.status());
        assertEquals(UserStatus.SUSPENDED, u.getStatus());
    }

    @Test
    void reactivatesASuspendedUser() {
        user(11, "VOLUNTEER", UserStatus.SUSPENDED);
        assertEquals(UserStatus.ACTIVE, service.changeStatus(1L, 11L, UserStatus.ACTIVE, null).status());
    }

    @Test
    void cannotChangeOwnStatus() {
        assertThrows(BadRequestException.class, () -> service.changeStatus(1L, 1L, UserStatus.SUSPENDED, null));
    }

    @Test
    void cannotChangeAnotherAdmin() {
        user(9, "ADMIN", UserStatus.ACTIVE);
        assertThrows(BadRequestException.class, () -> service.changeStatus(1L, 9L, UserStatus.SUSPENDED, null));
    }

    @Test
    void onlyActiveOrSuspendedAreAllowed() {
        user(11, "VOLUNTEER", UserStatus.ACTIVE);
        assertThrows(BadRequestException.class, () -> service.changeStatus(1L, 11L, UserStatus.PENDING, null));
    }

    @Test
    void pendingAccountsMustGoThroughVerification() {
        user(12, "NGO", UserStatus.PENDING);
        assertThrows(ConflictException.class, () -> service.changeStatus(1L, 12L, UserStatus.ACTIVE, null));
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(1L, 404L, UserStatus.SUSPENDED, null));
    }

    @Test
    void approvingNgoVerifiesAndActivates() {
        User u = user(12, "NGO", UserStatus.PENDING);
        NgoProfile n = ngo(12, VerificationStatus.PENDING);
        VerificationResponse r = service.approve(1L, "ngo", 12L);
        assertEquals(VerificationStatus.VERIFIED, n.getVerificationStatus());
        assertEquals(UserStatus.ACTIVE, u.getStatus());
        assertNotNull(n.getVerifiedAt());
        assertEquals(VerificationStatus.VERIFIED, r.status());
        assertEquals("NGO", r.type());
    }

    @Test
    void rejectingNgoNeedsAReason() {
        user(12, "NGO", UserStatus.PENDING);
        ngo(12, VerificationStatus.PENDING);
        assertThrows(BadRequestException.class, () -> service.reject(1L, "NGO", 12L, "  "));
    }

    @Test
    void rejectingNgoMarksUserRejected() {
        User u = user(12, "NGO", UserStatus.PENDING);
        NgoProfile n = ngo(12, VerificationStatus.PENDING);
        service.reject(1L, "NGO", 12L, "Invalid registration number");
        assertEquals(VerificationStatus.REJECTED, n.getVerificationStatus());
        assertEquals(UserStatus.REJECTED, u.getStatus());
    }

    @Test
    void alreadyDecidedRequestIsAConflict() {
        user(6, "NGO", UserStatus.ACTIVE);
        ngo(6, VerificationStatus.VERIFIED);
        assertThrows(ConflictException.class, () -> service.approve(1L, "NGO", 6L));
    }

    @Test
    void approvingDonorKeepsActiveUserActive() {
        User u = user(2, "DONOR", UserStatus.ACTIVE);
        DonorProfile d = donor(2, VerificationStatus.PENDING);
        service.approve(1L, "donor", 2L);
        assertEquals(VerificationStatus.VERIFIED, d.getVerificationStatus());
        assertEquals(UserStatus.ACTIVE, u.getStatus());
    }

    @Test
    void unknownTypeIsRejected() {
        assertThrows(BadRequestException.class, () -> service.approve(1L, "volunteer", 12L));
    }
}