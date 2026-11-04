package com.iskolar.dto;

import com.iskolar.model.ProviderType;

/**
 * Guest discovery filters (all nullable — null means "no filter").
 *
 * <p>Semantics in {@code ScholarshipDao.findActiveForDiscovery}:
 * a scholarship matches a program/year filter when it explicitly allows that
 * value <b>or</b> has an empty junction (open to all).
 *
 * <p>{@code keyword} is a free-text needle matched (case-insensitively,
 * substring) against the scholarship title and provider name. {@code %} and
 * {@code _} are treated literally, not as SQL wildcards.
 */
public record DiscoveryFilter(
        Integer programId,
        Integer yearLevel,
        String province,
        ProviderType providerType,
        String keyword) {

    public static DiscoveryFilter empty() {
        return new DiscoveryFilter(null, null, null, null, null);
    }
}
