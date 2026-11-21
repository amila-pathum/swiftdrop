package com.swiftdrop.parcel.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SriLankaDistrictsTest {

    @Test
    void readsTheDistrictAtTheEndOfAStoredAddress() {
        assertThat(SriLankaDistricts.districtOf("12 Temple Road, Colombo")).contains("Colombo");
        assertThat(SriLankaDistricts.districtOf("5 Lake View, Flat 2, Nuwara Eliya")).contains("Nuwara Eliya");
    }

    @Test
    void neverReturnsFreeText() {
        assertThat(SriLankaDistricts.districtOf("12 Temple Road, Springfield")).isEmpty();
        assertThat(SriLankaDistricts.districtOf("12 Temple Road")).isEmpty();
        assertThat(SriLankaDistricts.districtOf("")).isEmpty();
        assertThat(SriLankaDistricts.districtOf(null)).isEmpty();
    }

    @Test
    void patternAcceptsEveryDistrictAndNothingElse() {
        assertThat("Kandy".matches(SriLankaDistricts.PATTERN)).isTrue();
        assertThat("Trincomalee".matches(SriLankaDistricts.PATTERN)).isTrue();
        assertThat("kandy".matches(SriLankaDistricts.PATTERN)).isFalse();
        assertThat("Kandy City".matches(SriLankaDistricts.PATTERN)).isFalse();
    }
}
