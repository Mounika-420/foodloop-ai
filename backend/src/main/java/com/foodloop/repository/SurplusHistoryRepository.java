package com.foodloop.repository;

import com.foodloop.entity.Donation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/** Read-only helper for the AI surplus request: the donor's most recent quantities in a category. */
public interface SurplusHistoryRepository extends Repository<Donation, Long> {

    @Query("select d.quantityKg from Donation d "
            + "where d.donor.userId = :donorId and d.category.id = :categoryId "
            + "order by d.createdAt desc")
    List<BigDecimal> latestQuantities(@Param("donorId") Long donorId,
                                      @Param("categoryId") Integer categoryId,
                                      Pageable pageable);
}