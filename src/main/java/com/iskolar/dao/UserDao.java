package com.iskolar.dao;

import com.iskolar.model.Role;
import com.iskolar.model.ThemePref;
import com.iskolar.model.User;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/** DAO for {@code users}. Password hashing stays in the service layer. */
public class UserDao {

    private static final String BASE_COLUMNS =
            "user_id, email, password_hash, role, theme_pref, created_at";

    private final DataSource dataSource;

    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM users WHERE email = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by email: " + email, e);
        }
    }

    public Optional<User> findById(long userId) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM users WHERE user_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by id: " + userId, e);
        }
    }

    public User insert(String email, String passwordHash, Role role, ThemePref themePref) {
        String sql = "INSERT INTO users (email, password_hash, role, theme_pref) VALUES (?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, passwordHash);
            ps.setString(3, role.name());
            ps.setString(4, themePref.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated key returned for new user");
                }
                return new User(keys.getLong(1), email, passwordHash, role, themePref, LocalDateTime.now());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert user: " + email, e);
        }
    }

    public void updateThemePreference(long userId, ThemePref themePref) {
        String sql = "UPDATE users SET theme_pref = ? WHERE user_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, themePref.name());
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update theme preference for user " + userId, e);
        }
    }

    public int count() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count users", e);
        }
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.fromDb(rs.getString("role")),
                ThemePref.fromDb(rs.getString("theme_pref")),
                Dates.parseTimestamp(rs.getString("created_at")));
    }
}
