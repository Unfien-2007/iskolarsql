/**
 * Domain model (Phase 2) — immutable Java {@code Record}s mapped 1:1 to the
 * tables in {@code src/main/resources/db/schema.sql}, plus type-safe enums.
 *
 * <p>Planned records:
 * <ul>
 *   <li>{@code User}, {@code DegreeProgram}, {@code StudentProfile}</li>
 *   <li>{@code Scholarship}, {@code ScholarshipCriteria}, {@code Tag},
 *       {@code DocumentType}, {@code StudentDocument}, {@code Application}</li>
 * </ul>
 *
 * <p>Planned enums: {@code Role}, {@code SchoolType}, {@code ProviderType},
 * {@code ApplicationStatus}, {@code MatchTier}, {@code DeadlineUrgency}.
 */
package com.iskolar.model;
