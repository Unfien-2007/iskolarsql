/**
 * Business-logic layer between controllers and DAOs.
 *
 * <ul>
 *   <li>{@code MatchingService} (Phase 3) — the two-stage weighted engine:
 *       Stage 1 hard eligibility gates, Stage 2 weighted compatibility
 *       (Academic 35%, Financial 30%, Demographics/Tags 20%,
 *       Document Readiness 15%).</li>
 *   <li>{@code AuthService} — BCrypt login/registration, role checks.</li>
 *   <li>{@code ProfileService} — 4-step wizard persistence.</li>
 *   <li>{@code ApplicationService} — Kanban status transitions
 *       ({@code SAVED → PREPARING_DOCS → APPLIED → INTERVIEW → AWARDED/REJECTED}).</li>
 * </ul>
 */
package com.iskolar.service;
