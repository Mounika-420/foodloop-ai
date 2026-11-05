package com.foodloop.service;

import com.foodloop.dto.AuthResponse;
import com.foodloop.dto.RegisterRequest;
import com.foodloop.dto.RegisterResponse;
import com.foodloop.dto.ResetPasswordRequest;
import com.foodloop.dto.UserSummary;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.Location;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.PasswordResetToken;
import com.foodloop.entity.RevokedToken;
import com.foodloop.entity.Role;
import com.foodloop.entity.User;
import com.foodloop.entity.VolunteerProfile;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuthService {
    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";
    private static final long RESET_TOKEN_MINUTES = 30;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final VolunteerProfileRepository volunteerProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       LocationRepository locationRepository, DonorProfileRepository donorProfileRepository,
                       NgoProfileRepository ngoProfileRepository, VolunteerProfileRepository volunteerProfileRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       RevokedTokenRepository revokedTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService auditService, EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.locationRepository = locationRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.volunteerProfileRepository = volunteerProfileRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    // ------------------------------------------------------------------ register
    @Transactional
    public RegisterResponse register(RegisterRequest r) {
        String email = normalize(r.email());
        String roleName = r.role();
        if (!"DONOR".equals(roleName) && !"NGO".equals(roleName) && !"VOLUNTEER".equals(roleName)) {
            throw new BadRequestException("Role not allowed for self-registration");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        validateRoleFields(r);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " missing in database"));

        Location location = new Location();
        location.setAddressLine(r.addressLine().trim());
        location.setArea(r.area());
        location.setCity(r.city().trim());
        location.setState(r.state().trim());
        location.setPincode(r.pincode());
        location.setLatitude(r.latitude());
        location.setLongitude(r.longitude());
        location = locationRepository.save(location);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(r.password()));
        user.setFullName(r.fullName().trim());
        user.setPhone(r.phone());
        user.setRole(role);
        user.setStatus("VOLUNTEER".equals(roleName) ? UserStatus.ACTIVE : UserStatus.PENDING);   // donors/NGOs need admin verification
        user.setEmailVerified(false);
        user = userRepository.save(user);

        switch (roleName) {
            case "DONOR" -> {
                DonorProfile p = new DonorProfile();
                p.setUser(user);
                p.setOrganizationName(r.organizationName().trim());
                p.setDonorType(r.donorType());
                p.setFssaiLicenseNo(r.fssaiLicenseNo());
                p.setLocation(location);
                p.setVerificationStatus(VerificationStatus.PENDING);
                donorProfileRepository.save(p);
            }
            case "NGO" -> {
                if (ngoProfileRepository.existsByRegistrationNumber(r.registrationNumber().trim())) {
                    throw new ConflictException("An NGO with this registration number already exists");
                }
                NgoProfile p = new NgoProfile();
                p.setUser(user);
                p.setOrganizationName(r.organizationName().trim());
                p.setRegistrationNumber(r.registrationNumber().trim());
                p.setOrgType(r.orgType());
                p.setContactPerson(r.contactPerson().trim());
                p.setLocation(location);
                p.setDailyCapacityKg(r.dailyCapacityKg());
                p.setAvailableFrom(LocalTime.of(8, 0));
                p.setAvailableTo(LocalTime.of(21, 0));
                p.setBeneficiariesServed(0);
                p.setVerificationStatus(VerificationStatus.PENDING);
                ngoProfileRepository.save(p);
            }
            default -> {
                VolunteerProfile p = new VolunteerProfile();
                p.setUser(user);
                p.setVehicleType(r.vehicleType());
                p.setServiceRadiusKm(r.serviceRadiusKm() != null ? r.serviceRadiusKm() : new BigDecimal("10.0"));
                p.setAvailabilityStatus(Availability.AVAILABLE);
                p.setBaseLocation(location);
                p.setTotalDeliveries(0);
                volunteerProfileRepository.save(p);
            }
        }

        auditService.log(user.getId(), "REGISTER", "USER", user.getId(), null,
                Map.of("role", roleName, "status", user.getStatus().name()));
        String message = user.getStatus() == UserStatus.PENDING
                ? "Registration successful. Your account will be active after admin verification."
                : "Registration successful. You can log in now.";
        return new RegisterResponse(user.getId(), user.getEmail(), roleName, user.getStatus().name(), message);
    }

    private void validateRoleFields(RegisterRequest r) {
        switch (r.role()) {
            case "DONOR" -> {
                require(r.organizationName(), "organizationName");
                if (r.donorType() == null) throw new BadRequestException("donorType is required for donors");
            }
            case "NGO" -> {
                require(r.organizationName(), "organizationName");
                require(r.registrationNumber(), "registrationNumber");
                require(r.contactPerson(), "contactPerson");
                if (r.orgType() == null) throw new BadRequestException("orgType is required for NGOs");
                if (r.dailyCapacityKg() == null || r.dailyCapacityKg().signum() <= 0) {
                    throw new BadRequestException("dailyCapacityKg must be greater than 0 for NGOs");
                }
            }
            default -> {
                if (r.vehicleType() == null) throw new BadRequestException("vehicleType is required for volunteers");
            }
        }
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required for this role");
    }

    // ------------------------------------------------------------------ login / logout
    @Transactional
    public AuthResponse login(String rawEmail, String rawPassword) {
        User user = userRepository.findByEmail(normalize(rawEmail)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(rawPassword, hash);
        if (user == null || !passwordOk) {
            throw new BadCredentialsException(GENERIC_LOGIN_ERROR);       // same message for unknown email and wrong password
        }
        if (user.getStatus() == UserStatus.SUSPENDED) throw new DisabledException("Your account has been suspended");
        if (user.getStatus() == UserStatus.REJECTED) throw new DisabledException("Your registration was rejected");

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        auditService.log(user.getId(), "LOGIN", "USER", user.getId(), null, null);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().getName());
        return new AuthResponse(token, "Bearer", jwtService.getExpirySeconds(), toSummary(user));
    }

    /** Revokes the token's jti so it cannot be used again even before it expires. Invalid tokens are ignored. */
    @Transactional
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return;
        try {
            Claims claims = jwtService.parse(authorizationHeader.substring(7).trim());
            if (revokedTokenRepository.existsByJti(claims.getId())) return;
            RevokedToken revoked = new RevokedToken();
            revoked.setJti(claims.getId());
            revoked.setExpiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault()));
            revoked.setRevokedAt(LocalDateTime.now());
            revokedTokenRepository.save(revoked);
            auditService.log(Long.valueOf(claims.getSubject()), "LOGOUT", "USER", Long.valueOf(claims.getSubject()), null, null);
        } catch (JwtException | IllegalArgumentException ignored) {
            // expired / invalid token: nothing to revoke
        }
    }

    // ------------------------------------------------------------------ forgot / reset password
    /** Always succeeds from the caller's point of view, so attackers cannot discover which emails are registered. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        userRepository.findByEmail(normalize(rawEmail)).ifPresent(user -> {
            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            PasswordResetToken t = new PasswordResetToken();
            t.setUser(user);
            t.setTokenHash(TokenHasher.sha256Hex(token));              // only the hash is stored
            t.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));
            t.setUsed(false);
            passwordResetTokenRepository.save(t);
            auditService.log(user.getId(), "FORGOT_PASSWORD", "USER", user.getId(), null, null);
            emailService.sendPasswordReset(user.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken t = passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(request.token()))
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));
        if (t.isUsed() || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired reset link");
        }
        User user = t.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        t.setUsed(true);
        passwordResetTokenRepository.save(t);
        auditService.log(user.getId(), "RESET_PASSWORD", "USER", user.getId(), null, null);
    }

    // ------------------------------------------------------------------ helpers
    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole().getName(), u.getStatus().name());
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
