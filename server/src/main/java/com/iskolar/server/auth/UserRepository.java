package com.iskolar.server.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the server-side {@code users} table. Email comparison is
 * case-insensitive via {@code lower(email)}.
 */
@Repository
public class UserRepository {

    public static final RowMapper<UserRow> MAPPER = (rs, rowNum) -> new UserRow(
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getBoolean("is_active"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        List<UserRow> rows = jdbc.query(
                "SELECT user_id, email, password_hash, role, is_active FROM users WHERE lower(email) = lower(?)",
                MAPPER, email);
        return rows.stream().findFirst();
    }

    public Optional<UserRow> findById(long userId) {
        List<UserRow> rows = jdbc.query(
                "SELECT user_id, email, password_hash, role, is_active FROM users WHERE user_id = ?",
                MAPPER, userId);
        return rows.stream().findFirst();
    }

    public UserRow insert(String email, String passwordHash, String role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING user_id",
                Long.class, email, passwordHash, role);
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted user not found: " + id));
    }

    public void updatePassword(long userId, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ?, updated_at = now() WHERE user_id = ?",
                passwordHash, userId);
    }

    /** User row as stored on the server. */
    public record UserRow(long userId, String email, String passwordHash, String role, boolean isActive) {
    }
}
