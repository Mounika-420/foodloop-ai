package com.foodloop.repository;

import com.foodloop.entity.*;
import com.foodloop.entity.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DonationMatchRepository extends JpaRepository<DonationMatch, Long> {

    List<DonationMatch> findByDonation_IdOrderByRankPositionAsc(Long donationId);

    Page<DonationMatch> findByNgo_UserIdAndStatus(Long ngoId, MatchStatus status, Pageable pageable);

    Optional<DonationMatch> findByDonation_IdAndNgo_UserId(Long donationId, Long ngoId);

    List<DonationMatch> findByDonation_IdAndStatus(Long donationId, MatchStatus status);
}
