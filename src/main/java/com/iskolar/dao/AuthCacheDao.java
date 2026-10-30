package com.iskolar.dao;

import com.iskolar.model.Role;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * DAO for the {@code auth_cache} table: the offline credential cache.
 * Every successful online sign-in refreshes the cached bcrypt hash so the
 * student can keep signing in while the central server is unreachable.
 */
public class AuthCacheDao {

    public record CachedAuth(long serverUserId, String email, String passwordHash,
                             Role role, LocalDateTime lastOnlineAt) {
    }

    private final DataSource dataSource;

    public AuthCacheDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(long serverUserId, String email, String passwordHash, Role role) {
        String sql = "INSERT INTO auth_cache (user_id, email, password_hash, role, last_online_at) "
                + "VALUES (?, ?, ?, ?, datetime('now')) "
                + "ON CONFLICT(user_id) DO UPDATE SET email = excluded.email, "
                + "password_hash = excluded.password_hash, role = excluded.role, "
                + "last_online_at = excluded.last_online_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, serverUserId);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, role.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to cache credentials for " + email, e);
        }
    }

    public Optional<CachedAuth> findByEmail(String email) {
        String sql = "SELECT user_id, email, password_hash, role, last_online_at "
                + "FROM auth_cache WHERE email = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find cached credentials for " + email, e);
        }
    }

    public int count() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM auth_cache");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count cached credentials", e);
        }
    }

    /** Bumps the 'last seen online' timestamp (revalidation on reconnect). */
    public void touch(String email) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE auth_cache SET last_online_at = datetime('now') WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to touch cached credentials for " + email, e);
        }
    }

    /** Drops the cached credential (account revocation). */
    public void remove(String email) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM auth_cache WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to remove cached credentials for " + email, e);
        }
    }

    static CachedAuth map(ResultSet rs) throws SQLException {
        return new CachedAuth(
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.fromDb(rs.getString("role")),
                Dates.parseTimestamp(rs.getString("last_online_at")));
    }
}
