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
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Page<Feedback> findByStatus(FeedbackStatus status, Pageable pageable);

    boolean existsByDonation_IdAndFromUser_IdAndKind(Long donationId, Long fromUserId, FeedbackKind kind);
}
