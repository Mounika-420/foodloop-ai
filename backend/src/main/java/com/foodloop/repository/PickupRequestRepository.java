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
public interface PickupRequestRepository extends JpaRepository<PickupRequest, Long> {

    Optional<PickupRequest> findByDonation_Id(Long donationId);

    List<PickupRequest> findByVolunteer_UserIdAndStatusIn(Long volunteerId, Collection<PickupStatus> statuses);

    List<PickupRequest> findByStatus(PickupStatus status);

    Page<PickupRequest> findByNgo_UserId(Long ngoId, Pageable pageable);
}
