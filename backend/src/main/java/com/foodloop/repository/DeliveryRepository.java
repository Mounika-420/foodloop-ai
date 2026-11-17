package com.foodloop.repository;

import com.foodloop.entity.Delivery;
import com.foodloop.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByPickupRequest_Id(Long pickupRequestId);

    Page<Delivery> findByPickupRequest_Volunteer_UserIdAndStatusIn(Long volunteerId,
                                                                   Collection<DeliveryStatus> statuses,
                                                                   Pageable pageable);

    Page<Delivery> findByPickupRequest_Ngo_UserIdAndStatusIn(Long ngoId,
                                                             Collection<DeliveryStatus> statuses,
                                                             Pageable pageable);
}