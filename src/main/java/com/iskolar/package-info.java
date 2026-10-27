/**
 * Iskolar — Scholarship Finder &amp; Application Tracker.
 *
 * <p>Application entry points (delivered in Phase 4):
 * <ul>
 *   <li>{@code Launcher} — plain {@code main()} bootstrap, kept outside the
 *       JavaFX {@code Application} subclass so {@code mvn javafx:run} works
 *       without a module descriptor.</li>
 *   <li>{@code ScholarshipApp} — the JavaFX {@code Application} that builds the
 *       single primary {@code Stage} with a {@code BorderPane} root: collapsible
 *       sidebar rail (left) + dynamic center content swapped by
 *       {@code util.ViewNavigator}.</li>
 * </ul>
 *
 * <p>Layered architecture (MVC + DAO):
 * <pre>
 *   controller (C) ──► service (business rules) ──► dao (SQL) ──► SQLite via HikariCP
 *        ▲                                                          │
 *        └────────────── view (V): FXML + CSS ◄── model/dto (M) ◄───┘
 * </pre>
 */
package com.iskolar;
