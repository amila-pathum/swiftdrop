package com.swiftdrop.parcel.service;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The 25 districts SwiftDrop delivers to. Parcel creation accepts only these as
 * pickup and delivery cities, and stores each address as
 * {@code "<line1>[, <line2>], <district>"}.
 */
public final class SriLankaDistricts {

    /** Validation pattern for request fields; a compile-time constant for {@code @Pattern}. */
    public static final String PATTERN = "Ampara|Anuradhapura|Badulla|Batticaloa|Colombo|Galle"
        + "|Gampaha|Hambantota|Jaffna|Kalutara|Kandy|Kegalle|Kilinochchi|Kurunegala|Mannar"
        + "|Matale|Matara|Monaragala|Mullaitivu|Nuwara Eliya|Polonnaruwa|Puttalam|Ratnapura"
        + "|Trincomalee|Vavuniya";

    private static final Set<String> ALL =
        Arrays.stream(PATTERN.split("\\|")).collect(Collectors.toUnmodifiableSet());

    private SriLankaDistricts() { }

    /**
     * The district at the end of a stored address. Only a known district name is
     * ever returned, never free text, so the result is safe to show publicly.
     */
    public static Optional<String> districtOf(String storedAddress) {
        if (storedAddress == null) {
            return Optional.empty();
        }
        int comma = storedAddress.lastIndexOf(',');
        String last = (comma < 0 ? storedAddress : storedAddress.substring(comma + 1)).trim();
        return ALL.contains(last) ? Optional.of(last) : Optional.empty();
    }
}
