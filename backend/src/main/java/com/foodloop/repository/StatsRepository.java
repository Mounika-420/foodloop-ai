package com.foodloop.repository;

import com.foodloop.entity.Donation;
import com.foodloop.entity.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only aggregate queries for the admin dashboard. */
public interface StatsRepository extends Repository<Donation, Long> {

    /** Rows of [DonationStatus, Long count, BigDecimal kg]. */
    @Query("select d.status, count(d), sum(d.quantityKg) from Donation d group by d.status")
    List<Object[]> donationsByStatus();

    /** Rows of [String category, Long count, BigDecimal kg], busiest first. */
    @Query("select d.category.name, count(d), sum(d.quantityKg) from Donation d "
            + "group by d.category.name order by count(d) desc")
    List<Object[]> donationsByCategory();

    @Query("select count(d) from Donation d where d.createdAt >= :since")
    long countDonationsSince(@Param("since") LocalDateTime since);

    /** Rows of [DeliveryStatus, Long count]. */
    @Query("select d.status, count(d) from Delivery d group by d.status")
    List<Object[]> deliveriesByStatus();

    @Query("select sum(d.quantityReceivedKg) from Delivery d where d.status = :status")
    BigDecimal sumReceivedKg(@Param("status") DeliveryStatus status);

    /** Rows of [Availability, Long count]. */
    @Query("select v.availabilityStatus, count(v) from VolunteerProfile v group by v.availabilityStatus")
    List<Object[]> volunteersByAvailability();
}