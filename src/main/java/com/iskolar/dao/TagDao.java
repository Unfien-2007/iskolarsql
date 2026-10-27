package com.iskolar.dao;

import com.iskolar.model.Tag;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** DAO for {@code tags} and the {@code student_tags} junction (wizard Step 3). */
public class TagDao {

    private final DataSource dataSource;

    public TagDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Tag> findAll() {
        String sql = "SELECT tag_id, tag_code, tag_name, description FROM tags ORDER BY tag_name";
        List<Tag> tags = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                tags.add(map(rs));
            }
            return tags;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load tags", e);
        }
    }

    public Optional<Tag> findById(int tagId) {
        String sql = "SELECT tag_id, tag_code, tag_name, description FROM tags WHERE tag_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find tag: " + tagId, e);
        }
    }

    /** All tags currently attached to a student. */
    public List<Tag> findForStudent(int studentId) {
        String sql = """
                SELECT t.tag_id, t.tag_code, t.tag_name, t.description
                FROM student_tags st
                JOIN tags t ON t.tag_id = st.tag_id
                WHERE st.student_id = ?
                ORDER BY t.tag_name
                """;
        List<Tag> tags = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(map(rs));
                }
            }
            return tags;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load tags for student " + studentId, e);
        }
    }

    /** Atomically replaces the student's tag set (wizard Step 3 save). */
    public void replaceForStudent(int studentId, Collection<Integer> tagIds) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM student_tags WHERE student_id = ?")) {
                    del.setInt(1, studentId);
                    del.executeUpdate();
                }
                if (tagIds != null && !tagIds.isEmpty()) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO student_tags (student_id, tag_id) VALUES (?, ?)")) {
                        for (Integer tagId : tagIds) {
                            ins.setInt(1, studentId);
                            ins.setInt(2, tagId);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to replace tags for student " + studentId, e);
        }
    }

    static Tag map(ResultSet rs) throws SQLException {
        return new Tag(
                rs.getInt("tag_id"),
                rs.getString("tag_code"),
                rs.getString("tag_name"),
                rs.getString("description"));
    }
}
