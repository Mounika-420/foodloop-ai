package com.foodloop.repository;

import com.foodloop.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByPickupRequest_Id(Long pickupRequestId);
}