package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateParcelRequest(
    @NotBlank @Size(max = 150) String receiverName,
    @NotBlank @Pattern(regexp = "^(?=(?:[^0-9]*[0-9]){7})[+0-9 ()-]{7,30}$", message = "Invalid receiver phone") String receiverPhone,
    @NotBlank String pickupAddress,
    @NotBlank String deliveryAddress,
    @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal weight,
    @NotBlank @Pattern(regexp = "SMALL|MEDIUM|LARGE", message = "Size must be SMALL, MEDIUM or LARGE") String size,
    @NotBlank @Pattern(regexp = "STANDARD", message = "Unsupported parcel type") String parcelType
) {}
