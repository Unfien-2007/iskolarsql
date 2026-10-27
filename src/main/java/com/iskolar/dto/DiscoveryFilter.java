package com.iskolar.dto;

import com.iskolar.model.ProviderType;

/**
 * Guest discovery filters (all nullable — null means "no filter").
 *
 * <p>Semantics in {@code ScholarshipDao.findActiveForDiscovery}:
 * a scholarship matches a program/year filter when it explicitly allows that
 * value <b>or</b> has an empty junction (open to all).
 */
public record DiscoveryFilter(
        Integer programId,
        Integer yearLevel,
        String province,
        ProviderType providerType) {

    public static DiscoveryFilter empty() {
        return new DiscoveryFilter(null, null, null, null);
    }
}
