package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateParcelRequest(
    @NotBlank @Size(max = 150) String receiverName,
    @NotBlank @Pattern(regexp = "^(?=(?:[^0-9]*[0-9]){7})[+0-9 ()-]{7,30}$", message = "Invalid receiver phone") String receiverPhone,
    @NotBlank @Size(max = 255) String pickupAddressLine1,
    @Size(max = 255) String pickupAddressLine2,
    @NotBlank @Pattern(regexp = "Ampara|Anuradhapura|Badulla|Batticaloa|Colombo|Galle|Gampaha|Hambantota|Jaffna|Kalutara|Kandy|Kegalle|Kilinochchi|Kurunegala|Mannar|Matale|Matara|Monaragala|Mullaitivu|Nuwara Eliya|Polonnaruwa|Puttalam|Ratnapura|Trincomalee|Vavuniya", message = "Unsupported pickup district") String pickupCity,
    @NotBlank @Size(max = 255) String deliveryAddressLine1,
    @Size(max = 255) String deliveryAddressLine2,
    @NotBlank @Pattern(regexp = "Ampara|Anuradhapura|Badulla|Batticaloa|Colombo|Galle|Gampaha|Hambantota|Jaffna|Kalutara|Kandy|Kegalle|Kilinochchi|Kurunegala|Mannar|Matale|Matara|Monaragala|Mullaitivu|Nuwara Eliya|Polonnaruwa|Puttalam|Ratnapura|Trincomalee|Vavuniya", message = "Unsupported delivery district") String deliveryCity,
    @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal weight,
    @NotBlank @Pattern(regexp = "SMALL|MEDIUM|LARGE", message = "Size must be SMALL, MEDIUM or LARGE") String size,
    @NotBlank @Pattern(regexp = "STANDARD|EXPRESS", message = "Parcel type must be STANDARD or EXPRESS") String parcelType
) {}
