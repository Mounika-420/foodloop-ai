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
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_request_id", nullable = false, unique = true)
    private PickupRequest pickupRequest;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private LocalDateTime pickedUpAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime receiptConfirmedAt;

    private BigDecimal quantityReceivedKg;

    private BigDecimal lastLatitude;

    private BigDecimal lastLongitude;

    private String confirmationNote;

    private String failureReason;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
