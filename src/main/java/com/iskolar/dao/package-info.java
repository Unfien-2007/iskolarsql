/**
 * Data Access Objects (Phase 2) — one DAO per aggregate, JDBC with prepared
 * statements, connections obtained from the HikariCP pool in
 * {@code config.DatabaseConfig}. SQL lives here; business rules do NOT.
 *
 * <p>Planned DAOs:
 * <ul>
 *   <li>{@code UserDao} — authentication lookups, CRUD</li>
 *   <li>{@code DegreeProgramDao} — program lookup list</li>
 *   <li>{@code StudentProfileDao} — profile + tag + document vault writes</li>
 *   <li>{@code ScholarshipDao} — catalog reads for the discovery grid and
 *       full eligibility bundle (criteria, programs, years, tags, documents)</li>
 *   <li>{@code TagDao}, {@code DocumentDao} — lookup tables</li>
 *   <li>{@code ApplicationDao} — Kanban board CRUD + status transitions</li>
 * </ul>
 */
package com.iskolar.dao;
