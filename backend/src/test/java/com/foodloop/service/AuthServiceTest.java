package com.foodloop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodloop.dto.AuthResponse;
import com.foodloop.dto.RegisterRequest;
import com.foodloop.dto.RegisterResponse;
import com.foodloop.dto.ResetPasswordRequest;
import com.foodloop.entity.PasswordResetToken;
import com.foodloop.entity.RevokedToken;
import com.foodloop.entity.Role;
import com.foodloop.entity.User;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VehicleType;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.LocationRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.PasswordResetTokenRepository;
import com.foodloop.repository.RevokedTokenRepository;
import com.foodloop.repository.RoleRepository;
import com.foodloop.repository.UserRepository;
import com.foodloop.repository.VolunteerProfileRepository;
import com.foodloop.security.JwtService;
import com.foodloop.util.TokenHasher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock LocationRepository locationRepository;
    @Mock DonorProfileRepository donorProfileRepository;
    @Mock NgoProfileRepository ngoProfileRepository;
    @Mock VolunteerProfileRepository volunteerProfileRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock RevokedTokenRepository revokedTokenRepository;
    @Mock AuditService auditService;
    @Mock EmailService emailService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final JwtService jwtService = new JwtService("UnitTestSecretKeyThatIsAtLeast32Characters!", 15);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, roleRepository, locationRepository, donorProfileRepository,
                ngoProfileRepository, volunteerProfileRepository, passwordResetTokenRepository, revokedTokenRepository,
                encoder, jwtService, auditService, emailService);
    }

    private User user(String email, String rawPassword, UserStatus status, String roleName) {
        Role role = new Role();
        role.setId(2);
        role.setName(roleName);
        User u = new User();
        u.setId(7L);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setFullName("Test User");
        u.setPhone("9876543210");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private RegisterRequest volunteerRequest(String email) {
        return new RegisterRequest(email, "Passw0rd!x", "Vol One", "9876543210", "VOLUNTEER", "1 Main St", "Area",
                "Coimbatore", "Tamil Nadu", "641001", new BigDecimal("11.0"), new BigDecimal("76.9"),
                null, null, null, null, null, null, null, VehicleType.BIKE, null);
    }

    private RegisterRequest ngoRequestWithoutRegistrationNumber() {
        return new RegisterRequest("ngo@x.com", "Passw0rd!x", "Ngo Person", "9876543210", "NGO", "1 Main St", "Area",
                "Coimbatore", "Tamil Nadu", "641001", new BigDecimal("11.0"), new BigDecimal("76.9"),
                "Helping Hands", null, null, null, com.foodloop.entity.enums.OrgType.NGO, "Contact",
                new BigDecimal("100"), null, null);
    }

    // ---------------------------------------------------------------- login
    @Test
    void loginSucceedsWithCorrectCredentials() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "DONOR")));
        AuthResponse r = service.login("a@b.com", "Passw0rd!x");
        assertEquals("Bearer", r.tokenType());
        assertEquals("DONOR", r.user().role());
        assertFalse(r.accessToken().isBlank());
        assertEquals("7", jwtService.parse(r.accessToken()).getSubject());
        verify(userRepository).save(any(User.class));          // lastLoginAt updated
    }

    @Test
    void loginNormalisesEmail() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "NGO")));
        assertEquals("NGO", service.login("  A@B.com ", "Passw0rd!x").user().role());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameError() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "DONOR")));
        when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());
        BadCredentialsException wrong = assertThrows(BadCredentialsException.class, () -> service.login("a@b.com", "WrongPass1!"));
        BadCredentialsException unknown = assertThrows(BadCredentialsException.class, () -> service.login("nobody@b.com", "WrongPass1!"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void suspendedAndRejectedUsersCannotLogin() {
        when(userRepository.findByEmail("s@b.com")).thenReturn(Optional.of(user("s@b.com", "Passw0rd!x", UserStatus.SUSPENDED, "DONOR")));
        when(userRepository.findByEmail("r@b.com")).thenReturn(Optional.of(user("r@b.com", "Passw0rd!x", UserStatus.REJECTED, "DONOR")));
        assertThrows(DisabledException.class, () -> service.login("s@b.com", "Passw0rd!x"));
        assertThrows(DisabledException.class, () -> service.login("r@b.com", "Passw0rd!x"));
    }

    @Test
    void pendingUsersCanLogInToSeeVerificationStatus() {
        when(userRepository.findByEmail("p@b.com")).thenReturn(Optional.of(user("p@b.com", "Passw0rd!x", UserStatus.PENDING, "NGO")));
        assertEquals("PENDING", service.login("p@b.com", "Passw0rd!x").user().status());
    }

    // ---------------------------------------------------------------- register
    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("v@x.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.register(volunteerRequest("v@x.com")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNgoRequiresRegistrationNumber() {
        Role ngo = new Role();
        ngo.setName("NGO");
        when(roleRepository.findByName("NGO")).thenReturn(Optional.of(ngo));
        assertThrows(BadRequestException.class, () -> service.register(ngoRequestWithoutRegistrationNumber()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerVolunteerCreatesActiveUserWithHashedPassword() {
        Role vol = new Role();
        vol.setId(4);
        vol.setName("VOLUNTEER");
        when(roleRepository.findByName("VOLUNTEER")).thenReturn(Optional.of(vol));
        when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        RegisterResponse res = service.register(volunteerRequest("  New@X.com "));

        assertEquals("ACTIVE", res.status());
        assertEquals("new@x.com", res.email());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNotEquals("Passw0rd!x", userCaptor.getValue().getPasswordHash());
        assertTrue(encoder.matches("Passw0rd!x", userCaptor.getValue().getPasswordHash()));
        verify(volunteerProfileRepository).save(any(VolunteerProfile.class));
    }

    @Test
    void adminRoleCannotBeSelfRegistered() {
        RegisterRequest r = new RegisterRequest("x@x.com", "Passw0rd!x", "X", "9876543210", "ADMIN", "a", null, "c", "s",
                "641001", BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null, null, null, null, null);
        assertThrows(BadRequestException.class, () -> service.register(r));
    }

    // ---------------------------------------------------------------- logout
    @Test
    void logoutRevokesTokenId() {
        String token = jwtService.generateToken(7L, "a@b.com", "DONOR");
        when(revokedTokenRepository.existsByJti(anyString())).thenReturn(false);
        service.logout("Bearer " + token);
        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(captor.capture());
        assertEquals(jwtService.parse(token).getId(), captor.getValue().getJti());
    }

    @Test
    void logoutIgnoresGarbageOrMissingToken() {
        service.logout("Bearer not.a.jwt");
        service.logout(null);
        service.logout("Basic abc");
        verify(revokedTokenRepository, never()).save(any(RevokedToken.class));
    }

    // ---------------------------------------------------------------- forgot / reset
    @Test
    void forgotPasswordForUnknownEmailDoesNothingAndDoesNotFail() {
        when(userRepository.findByEmail("none@x.com")).thenReturn(Optional.empty());
        service.forgotPassword("none@x.com");
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void forgotPasswordStoresOnlyTheHashOfTheEmailedToken() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "DONOR")));
        service.forgotPassword("a@b.com");
        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> emailed = ArgumentCaptor.forClass(String.class);
        verify(passwordResetTokenRepository).save(stored.capture());
        verify(emailService).sendPasswordReset(anyString(), emailed.capture());
        assertEquals(64, emailed.getValue().length());
        assertNotEquals(emailed.getValue(), stored.getValue().getTokenHash());
        assertEquals(TokenHasher.sha256Hex(emailed.getValue()), stored.getValue().getTokenHash());
        assertTrue(stored.getValue().getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void resetPasswordRejectsUnknownExpiredOrUsedToken() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "DONOR"));
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        PasswordResetToken used = new PasswordResetToken();
        used.setUser(user("a@b.com", "Passw0rd!x", UserStatus.ACTIVE, "DONOR"));
        used.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        used.setUsed(true);
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex("unknown"))).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex("expired"))).thenReturn(Optional.of(expired));
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex("used"))).thenReturn(Optional.of(used));
        assertThrows(BadRequestException.class, () -> service.resetPassword(new ResetPasswordRequest("unknown", "NewPassw0rd!")));
        assertThrows(BadRequestException.class, () -> service.resetPassword(new ResetPasswordRequest("expired", "NewPassw0rd!")));
        assertThrows(BadRequestException.class, () -> service.resetPassword(new ResetPasswordRequest("used", "NewPassw0rd!")));
    }

    @Test
    void resetPasswordUpdatesPasswordAndMarksTokenUsed() {
        User u = user("a@b.com", "OldPassw0rd!", UserStatus.ACTIVE, "DONOR");
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(u);
        t.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex("good"))).thenReturn(Optional.of(t));
        service.resetPassword(new ResetPasswordRequest("good", "NewPassw0rd!"));
        assertTrue(encoder.matches("NewPassw0rd!", u.getPasswordHash()));
        assertTrue(t.isUsed());
    }
}
