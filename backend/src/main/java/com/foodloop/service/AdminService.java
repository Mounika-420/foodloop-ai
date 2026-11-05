package com.foodloop.service;

import com.foodloop.dto.AdminUserResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.dto.VerificationResponse;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.User;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SORTABLE = Set.of("createdAt", "email", "fullName", "lastLoginAt");

    private final UserRepository userRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        DonorProfileRepository donorProfileRepository,
                        NgoProfileRepository ngoProfileRepository,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminUserResponse> users(String role, UserStatus status, String q, Pageable pageable) {
        String r = role == null ? "" : role.trim().toUpperCase();
        return PagedResult.from(userRepository.search(r, status, clean(q), safe(pageable)).map(AdminService::toUser));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(Long id) {
        return toUser(find(id));
    }

    @Transactional
    public AdminUserResponse changeStatus(Long adminId, Long userId, UserStatus newStatus, String reason) {
        if (newStatus != UserStatus.ACTIVE && newStatus != UserStatus.SUSPENDED) {
            throw new BadRequestException("Status must be ACTIVE or SUSPENDED");
        }
        if (adminId.equals(userId)) {
            throw new BadRequestException("You cannot change your own status");
        }
        User u = find(userId);
        if ("ADMIN".equals(u.getRole().getName())) {
            throw new BadRequestException("Admin accounts cannot be changed here");
        }
        UserStatus old = u.getStatus();
        if (old == UserStatus.PENDING || old == UserStatus.REJECTED) {
            throw new ConflictException("This account is " + old + "; use the verification endpoints instead");
        }
        if (old == newStatus) {
            return toUser(u);
        }
        u.setStatus(newStatus);
        userRepository.save(u);

        Map<String, Object> before = new HashMap<>();
        before.put("status", old.name());
        Map<String, Object> after = new HashMap<>();
        after.put("status", newStatus.name());
        if (reason != null && !reason.isBlank()) {
            after.put("reason", reason.trim());
        }
        auditService.log(adminId, newStatus == UserStatus.SUSPENDED ? "SUSPEND_USER" : "ACTIVATE_USER",
                "USER", userId, before, after);
        return toUser(u);
    }

    @Transactional(readOnly = true)
    public List<VerificationResponse> verifications(String type, VerificationStatus status) {
        VerificationStatus st = status == null ? VerificationStatus.PENDING : status;
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!t.isEmpty() && !t.equals("NGO") && !t.equals("DONOR")) {
            throw new BadRequestException("Type must be DONOR or NGO");
        }
        List<VerificationResponse> out = new ArrayList<>();
        if (t.isEmpty() || t.equals("NGO")) {
            for (NgoProfile n : ngoProfileRepository.findByVerificationStatus(st)) {
                User u = n.getUser();
                out.add(toVerification(u, "NGO", n.getOrganizationName(), n.getRegistrationNumber(), n.getVerificationStatus()));
            }
        }
        if (t.isEmpty() || t.equals("DONOR")) {
            for (DonorProfile d : donorProfileRepository.findByVerificationStatus(st, PageRequest.of(0, 200)).getContent()) {
                User u = userRepository.findById(d.getUserId()).orElse(null);
                if (u != null) {
                    out.add(toVerification(u, "DONOR", null, null, d.getVerificationStatus()));
                }
            }
        }
        out.sort(Comparator.comparing(VerificationResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Transactional
    public VerificationResponse approve(Long adminId, String type, Long userId) {
        return decide(adminId, type, userId, true, null);
    }

    @Transactional
    public VerificationResponse reject(Long adminId, String type, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject a request");
        }
        return decide(adminId, type, userId, false, reason.trim());
    }

    private VerificationResponse decide(Long adminId, String type, Long userId, boolean approve, String reason) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!t.equals("NGO") && !t.equals("DONOR")) {
            throw new BadRequestException("Type must be DONOR or NGO");
        }
        User user = find(userId);
        VerificationStatus target = approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;
        String org = null;
        String reg = null;
        VerificationStatus old;

        if (t.equals("NGO")) {
            NgoProfile p = ngoProfileRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("NGO profile not found"));
            old = p.getVerificationStatus();
            requirePending(old);
            p.setVerificationStatus(target);
            p.setVerifiedBy(userRepository.getReferenceById(adminId));
            p.setVerifiedAt(LocalDateTime.now());
            ngoProfileRepository.save(p);
            org = p.getOrganizationName();
            reg = p.getRegistrationNumber();
        } else {
            DonorProfile p = donorProfileRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found"));
            old = p.getVerificationStatus();
            requirePending(old);
            p.setVerificationStatus(target);
            donorProfileRepository.save(p);
        }

        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(approve ? UserStatus.ACTIVE : UserStatus.REJECTED);
            userRepository.save(user);
        }

        Map<String, Object> before = new HashMap<>();
        before.put("verificationStatus", old.name());
        Map<String, Object> after = new HashMap<>();
        after.put("verificationStatus", target.name());
        after.put("type", t);
        if (reason != null) {
            after.put("reason", reason);
        }
        auditService.log(adminId, approve ? "APPROVE_VERIFICATION" : "REJECT_VERIFICATION",
                "USER", userId, before, after);
        return toVerification(user, t, org, reg, target);
    }

    private static void requirePending(VerificationStatus s) {
        if (s != VerificationStatus.PENDING) {
            throw new ConflictException("This request was already " + s);
        }
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static AdminUserResponse toUser(User u) {
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole().getName(), u.getStatus(), u.isEmailVerified(), u.getLastLoginAt(), u.getCreatedAt());
    }

    private static VerificationResponse toVerification(User u, String type, String org, String reg, VerificationStatus st) {
        return new VerificationResponse(u.getId(), type, u.getFullName(), u.getEmail(), org, reg, st, u.getCreatedAt());
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static Pageable safe(Pageable p) {
        Sort sort = Sort.by(p.getSort().stream().filter(o -> SORTABLE.contains(o.getProperty())).toList());
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return PageRequest.of(p.getPageNumber(), Math.min(p.getPageSize(), MAX_PAGE_SIZE), sort);
    }
}