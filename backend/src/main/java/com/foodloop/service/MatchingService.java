package com.foodloop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.dto.MatchResponse;
import com.foodloop.dto.NgoOfferResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.Donation;
import com.foodloop.entity.DonationMatch;
import com.foodloop.entity.Location;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.NgoRequirement;
import com.foodloop.entity.User;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.MatchStatus;
import com.foodloop.entity.enums.RequirementStatus;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.InvalidStatusTransitionException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.mapper.DonationMapper;
import com.foodloop.repository.DonationMatchRepository;
import com.foodloop.repository.DonationRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.NgoRequirementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MatchingService {

    static final int TOP_N = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<DonationStatus> COMMITTED = List.of(
            DonationStatus.ACCEPTED, DonationStatus.VOLUNTEER_ASSIGNED, DonationStatus.PICKED_UP,
            DonationStatus.IN_TRANSIT, DonationStatus.DELIVERED, DonationStatus.COMPLETED);

    private final DonationMatchRepository matchRepository;
    private final DonationRepository donationRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final NgoRequirementRepository requirementRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public MatchingService(DonationMatchRepository matchRepository,
                           DonationRepository donationRepository,
                           NgoProfileRepository ngoProfileRepository,
                           NgoRequirementRepository requirementRepository,
                           AuditService auditService,
                           ObjectMapper objectMapper) {
        this.matchRepository = matchRepository;
        this.donationRepository = donationRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.requirementRepository = requirementRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    private AiRanker aiRanker;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAiRanker(AiRanker aiRanker) {
        this.aiRanker = aiRanker;
    }

    /** Rule-based hard filters first; the AI service (when configured) only re-orders the eligible NGOs. */
    private List<RuleBasedMatcher.Result> rankWithAi(Donation d, RuleBasedMatcher.DonationInfo info,
                                                     List<RuleBasedMatcher.Candidate> candidates) {
        if (aiRanker == null || !aiRanker.isEnabled()) {
            return RuleBasedMatcher.rank(info, candidates, TOP_N);
        }
        List<RuleBasedMatcher.Result> eligible = RuleBasedMatcher.rank(info, candidates, Integer.MAX_VALUE);
        if (eligible.isEmpty()) {
            return eligible;
        }
        return aiRanker.rerank(d.getId(), d.getCategory().getName(), info, eligible, candidates, TOP_N);
    }

    /** Ranks NGOs for a freshly created donation. Returns how many offers were created. */
    @Transactional
    public int matchDonation(Donation d) {
        if (d.getStatus() != DonationStatus.AVAILABLE) {
            return 0;
        }
        Integer categoryId = d.getCategory().getId();
        List<RuleBasedMatcher.Candidate> candidates = new ArrayList<>();
        Map<Long, NgoProfile> byId = new java.util.HashMap<>();
        for (NgoProfile n : ngoProfileRepository.findByVerificationStatus(VerificationStatus.VERIFIED)) {
            User u = n.getUser();
            if (u == null || u.getStatus() != UserStatus.ACTIVE || n.getLocation() == null) {
                continue;
            }
            Location nl = n.getLocation();
            BigDecimal openReq = openRequirementKg(n.getUserId(), categoryId);
            candidates.add(new RuleBasedMatcher.Candidate(
                    n.getUserId(),
                    nl.getLatitude().doubleValue(),
                    nl.getLongitude().doubleValue(),
                    remainingCapacity(n).doubleValue(),
                    openReq == null ? null : openReq.doubleValue(),
                    n.getAvailableFrom(),
                    n.getAvailableTo()));
            byId.put(n.getUserId(), n);
        }

        Location pl = d.getPickupLocation();
        RuleBasedMatcher.DonationInfo info = new RuleBasedMatcher.DonationInfo(
                d.getQuantityKg().doubleValue(),
                pl.getLatitude().doubleValue(),
                pl.getLongitude().doubleValue(),
                LocalDateTime.now(),
                d.getPickupWindowStart(),
                d.getPickupWindowEnd(),
                d.getExpiresAt());

        List<RuleBasedMatcher.Result> ranked = rankWithAi(d, info, candidates);
        if (ranked.isEmpty()) {
            return 0;
        }
        for (RuleBasedMatcher.Result r : ranked) {
            DonationMatch dm = new DonationMatch();
            dm.setDonation(d);
            dm.setNgo(byId.get(r.ngoId()));
            dm.setMatchScore(r.score());
            dm.setDistanceKm(r.distanceKm());
            dm.setRankPosition(r.rank());
            dm.setScoreBreakdown(toJson(r.breakdown()));
            dm.setStatus(MatchStatus.NOTIFIED);
            matchRepository.save(dm);
        }
        if (!d.getStatus().canTransitionTo(DonationStatus.MATCHED)) {
            throw new InvalidStatusTransitionException("Cannot move donation from " + d.getStatus() + " to MATCHED");
        }
        d.setStatus(DonationStatus.MATCHED);
        donationRepository.save(d);
        auditService.log(null, "AUTO_MATCH", "DONATION", d.getId(), null, Map.of("offers", ranked.size()));
        return ranked.size();
    }

    @Transactional(readOnly = true)
    public boolean hasOffer(Long donationId, Long ngoId) {
        return matchRepository.findByDonation_IdAndNgo_UserId(donationId, ngoId)
                .map(m -> m.getStatus() == MatchStatus.NOTIFIED || m.getStatus() == MatchStatus.ACCEPTED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> matchesForDonation(Long userId, String role, Long donationId) {
        Donation d = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        boolean admin = "ADMIN".equals(role);
        if (!admin && !d.getDonor().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Donation not found");
        }
        return matchRepository.findByDonation_IdOrderByRankPositionAsc(donationId).stream()
                .map(this::toMatch).toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<NgoOfferResponse> recommended(Long ngoId, Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DonationMatch> page = matchRepository.findByNgo_UserIdAndStatus(ngoId, MatchStatus.NOTIFIED, p);
        return PagedResult.from(page.map(this::toOffer));
    }

    @Transactional
    public MatchResponse accept(Long ngoId, Long matchId) {
        DonationMatch m = findOwned(ngoId, matchId);
        requireNotified(m);
        Donation d = m.getDonation();
        LocalDateTime now = LocalDateTime.now();
        if (d.getStatus() != DonationStatus.MATCHED) {
            throw new ConflictException("This donation is no longer open for acceptance (" + d.getStatus() + ")");
        }
        if (!d.getExpiresAt().isAfter(now)) {
            throw new ConflictException("This donation has already expired");
        }
        NgoProfile ngo = m.getNgo();
        BigDecimal remaining = remainingCapacity(ngo);
        if (d.getQuantityKg().compareTo(remaining) > 0) {
            throw new ConflictException("Accepting " + d.getQuantityKg()
                    + " kg would exceed your remaining daily capacity of " + remaining + " kg");
        }
        if (!d.getStatus().canTransitionTo(DonationStatus.ACCEPTED)) {
            throw new InvalidStatusTransitionException("Cannot move donation from " + d.getStatus() + " to ACCEPTED");
        }
        d.setStatus(DonationStatus.ACCEPTED);
        d.setAcceptedNgo(ngo);
        try {
            donationRepository.saveAndFlush(d);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Another NGO accepted this donation first");
        }
        m.setStatus(MatchStatus.ACCEPTED);
        m.setRespondedAt(now);
        matchRepository.save(m);
        for (DonationMatch other : matchRepository.findByDonation_IdAndStatus(d.getId(), MatchStatus.NOTIFIED)) {
            if (!other.getId().equals(m.getId())) {
                other.setStatus(MatchStatus.WITHDRAWN);
                other.setRespondedAt(now);
                matchRepository.save(other);
            }
        }
        auditService.log(ngoId, "ACCEPT_MATCH", "DONATION_MATCH", m.getId(), null, Map.of("donationId", d.getId()));
        return toMatch(m);
    }

    @Transactional
    public MatchResponse reject(Long ngoId, Long matchId, String reason) {
        DonationMatch m = findOwned(ngoId, matchId);
        requireNotified(m);
        LocalDateTime now = LocalDateTime.now();
        m.setStatus(MatchStatus.REJECTED);
        m.setRespondedAt(now);
        m.setRejectionReason(reason == null || reason.isBlank() ? null : reason.trim());
        matchRepository.save(m);

        Donation d = m.getDonation();
        boolean othersPending = matchRepository.findByDonation_IdAndStatus(d.getId(), MatchStatus.NOTIFIED).stream()
                .anyMatch(o -> !o.getId().equals(m.getId()));
        if (!othersPending && d.getStatus() == DonationStatus.MATCHED
                && d.getStatus().canTransitionTo(DonationStatus.REJECTED_ALL)) {
            d.setStatus(DonationStatus.REJECTED_ALL);
            donationRepository.save(d);
        }
        auditService.log(ngoId, "REJECT_MATCH", "DONATION_MATCH", m.getId(), null, Map.of("donationId", d.getId()));
        return toMatch(m);
    }

    private DonationMatch findOwned(Long ngoId, Long matchId) {
        DonationMatch m = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
        if (!m.getNgo().getUserId().equals(ngoId)) {
            throw new ResourceNotFoundException("Match not found");
        }
        return m;
    }

    private static void requireNotified(DonationMatch m) {
        if (m.getStatus() != MatchStatus.NOTIFIED) {
            throw new ConflictException("This offer was already " + m.getStatus());
        }
    }

    private BigDecimal remainingCapacity(NgoProfile n) {
        BigDecimal used = donationRepository.sumAcceptedKgSince(n.getUserId(), COMMITTED, LocalDate.now().atStartOfDay());
        BigDecimal daily = n.getDailyCapacityKg() == null ? BigDecimal.ZERO : n.getDailyCapacityKg();
        BigDecimal rem = daily.subtract(used == null ? BigDecimal.ZERO : used);
        return rem.signum() < 0 ? BigDecimal.ZERO : rem;
    }

    private BigDecimal openRequirementKg(Long ngoId, Integer categoryId) {
        BigDecimal total = null;
        for (NgoRequirement r : requirementRepository.findByNgo_UserIdAndStatus(ngoId, RequirementStatus.OPEN)) {
            if (r.getCategory() != null && r.getCategory().getId().equals(categoryId)
                    && r.getRequiredQuantityKg() != null) {
                total = total == null ? r.getRequiredQuantityKg() : total.add(r.getRequiredQuantityKg());
            }
        }
        return total;
    }

    private MatchResponse toMatch(DonationMatch m) {
        return new MatchResponse(m.getId(), m.getDonation().getId(), m.getNgo().getUserId(),
                m.getNgo().getOrganizationName(), m.getRankPosition(), m.getMatchScore(), m.getDistanceKm(),
                parse(m.getScoreBreakdown()), m.getStatus(), m.getRespondedAt(), m.getRejectionReason());
    }

    private NgoOfferResponse toOffer(DonationMatch m) {
        return new NgoOfferResponse(m.getId(), m.getRankPosition(), m.getMatchScore(), m.getDistanceKm(),
                parse(m.getScoreBreakdown()), m.getStatus(), DonationMapper.toResponse(m.getDonation()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise score breakdown", e);
        }
    }

    /** Tolerates a JSON column that was stored as an object or as a JSON-encoded string. */
    private Object parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n.isTextual()) {
                n = objectMapper.readTree(n.asText());
            }
            return objectMapper.convertValue(n, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}