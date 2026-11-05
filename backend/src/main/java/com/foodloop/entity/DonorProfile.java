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
@Table(name = "donor_profiles")
@Getter
@Setter
@NoArgsConstructor
public class DonorProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String organizationName;

    @Enumerated(EnumType.STRING)
    private DonorType donorType;

    private String fssaiLicenseNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verified_by", nullable = true)
    private User verifiedBy;

    private LocalDateTime verifiedAt;
}
