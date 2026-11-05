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
public interface DonationRepository extends JpaRepository<Donation, Long> {

    Page<Donation> findByDonor_UserId(Long donorId, Pageable pageable);

    Page<Donation> findByStatusIn(Collection<DonationStatus> statuses, Pageable pageable);

    List<Donation> findByStatusInAndExpiresAtBefore(Collection<DonationStatus> statuses, LocalDateTime time);

    List<Donation> findByStatusInAndExpiresAtBetween(Collection<DonationStatus> statuses, LocalDateTime from, LocalDateTime to);

    long countByStatus(DonationStatus status);

    Optional<Donation> findByDonationCode(String code);

    Page<Donation> findByAcceptedNgo_UserId(Long ngoId, Pageable pageable);
}
