package com.foodloop.repository;

import com.foodloop.entity.PickupRequest;
import com.foodloop.entity.enums.PickupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PickupRequestRepository extends JpaRepository<PickupRequest, Long> {

    boolean existsByDonation_Id(Long donationId);

    List<PickupRequest> findByStatusOrderByCreatedAtAsc(PickupStatus status);

    List<PickupRequest> findByVolunteer_UserIdAndStatusInOrderByScheduledPickupAtAsc(
            Long volunteerId, Collection<PickupStatus> statuses);

    List<PickupRequest> findByNgo_UserIdOrderByCreatedAtDesc(Long ngoId);
}