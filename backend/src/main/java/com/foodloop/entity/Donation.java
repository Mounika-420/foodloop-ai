package com.foodloop.entity;

import com.foodloop.entity.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String donationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfile donor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private FoodCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "food_item_id", nullable = true)
    private FoodItem foodItem;

    private String foodName;

    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    private Unit unit;

    private BigDecimal quantityKg;

    private LocalDateTime preparedAt;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private StorageCondition storageCondition;

    private String allergens;

    @Column(name = "is_vegetarian")
    private boolean vegetarian;

    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pickup_location_id", nullable = false)
    private Location pickupLocation;

    private LocalDateTime pickupWindowStart;

    private LocalDateTime pickupWindowEnd;

    private boolean safetyDeclared;

    @Enumerated(EnumType.STRING)
    private DonationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "accepted_ngo_id", nullable = true)
    private NgoProfile acceptedNgo;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
