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
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUser_IdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_IdAndReadFalse(Long userId);
}
