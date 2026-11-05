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
@Table(name = "predictions")
@Getter
@Setter
@NoArgsConstructor
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PredictionType predictionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "donor_id", nullable = true)
    private DonorProfile donor;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "ngo_id", nullable = true)
    private NgoProfile ngo;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "donation_id", nullable = true)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true)
    private FoodCategory category;

    @JdbcTypeCode(SqlTypes.JSON)
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    private String outputJson;

    private BigDecimal predictedValue;

    private String modelName;

    private String modelVersion;

    private boolean trainedOnSynthetic = true;

    private Integer latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
