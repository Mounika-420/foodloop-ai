package com.foodloop.dto;

import com.foodloop.entity.enums.DonorType;
import com.foodloop.entity.enums.OrgType;
import com.foodloop.entity.enums.VehicleType;
import com.foodloop.validation.StrongPassword;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One request for all self-registering roles. Role-specific fields are checked in AuthService.
 * ADMIN accounts can never be created through this endpoint.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @StrongPassword String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^[0-9+]{10,15}$", message = "Phone must be 10-15 digits") String phone,
        @NotBlank @Pattern(regexp = "^(DONOR|NGO|VOLUNTEER)$", message = "Role must be DONOR, NGO or VOLUNTEER") String role,
        @NotBlank @Size(max = 255) String addressLine,
        @Size(max = 100) String area,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be 6 digits") String pincode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        // donor + NGO
        @Size(max = 150) String organizationName,
        // donor only
        DonorType donorType,
        @Size(max = 20) String fssaiLicenseNo,
        // NGO only
        @Size(max = 50) String registrationNumber,
        OrgType orgType,
        @Size(max = 120) String contactPerson,
        @DecimalMin(value = "0.01", message = "Daily capacity must be positive") BigDecimal dailyCapacityKg,
        // volunteer only
        VehicleType vehicleType,
        @DecimalMin(value = "0.1", message = "Service radius must be positive") BigDecimal serviceRadiusKm) {
}
