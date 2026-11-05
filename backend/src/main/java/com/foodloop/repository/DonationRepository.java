package com.foodloop.repository;

import com.foodloop.entity.*;
import com.foodloop.entity.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select sum(d.quantityKg) from Donation d "
            + "where d.acceptedNgo.userId = :ngoId and d.status in :statuses and d.updatedAt >= :since")
    java.math.BigDecimal sumAcceptedKgSince(@Param("ngoId") Long ngoId,
                                            @Param("statuses") Collection<DonationStatus> statuses,
                                            @Param("since") LocalDateTime since);

    Optional<Donation> findByDonationCode(String code);

    Page<Donation> findByAcceptedNgo_UserId(Long ngoId, Pageable pageable);

    @Query("select d from Donation d "
            + "where d.donor.userId = :donorId and d.status in :statuses "
            + "and lower(d.foodName) like lower(concat('%', :q, '%'))")
    Page<Donation> searchMine(@Param("donorId") Long donorId,
                              @Param("statuses") Collection<DonationStatus> statuses,
                              @Param("q") String q,
                              Pageable pageable);

    @Query("select d from Donation d "
            + "where d.status = :status and d.expiresAt > :now "
            + "and (:category = '' or lower(d.category.name) = lower(:category)) "
            + "and lower(d.foodName) like lower(concat('%', :q, '%'))")
    Page<Donation> searchAvailable(@Param("status") DonationStatus status,
                                   @Param("now") LocalDateTime now,
                                   @Param("category") String category,
                                   @Param("q") String q,
                                   Pageable pageable);
}