package com.iskolar.dao;

import com.iskolar.model.DocumentType;
import com.iskolar.model.StudentDocument;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DAO for {@code document_types} and {@code student_documents} (wizard Step 4). */
public class DocumentDao {

    private final DataSource dataSource;

    public DocumentDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<DocumentType> findAll() {
        String sql = "SELECT document_type_id, doc_code, doc_name, description"
                + " FROM document_types ORDER BY document_type_id";
        List<DocumentType> docs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                docs.add(mapType(rs));
            }
            return docs;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load document types", e);
        }
    }

    /** The student's Document Readiness Vault (all rows, ready or not). */
    public List<StudentDocument> findForStudent(int studentId) {
        String sql = "SELECT student_id, document_type_id, is_ready, updated_at"
                + " FROM student_documents WHERE student_id = ? ORDER BY document_type_id";
        List<StudentDocument> docs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(mapStudentDoc(rs));
                }
            }
            return docs;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load documents for student " + studentId, e);
        }
    }

    /**
     * Atomically replaces the student's vault state.
     * Only keys present in the map are written (delete-then-insert).
     */
    public void replaceForStudent(int studentId, Map<Integer, Boolean> readyByDocTypeId) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM student_documents WHERE student_id = ?")) {
                    del.setInt(1, studentId);
                    del.executeUpdate();
                }
                if (readyByDocTypeId != null && !readyByDocTypeId.isEmpty()) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO student_documents (student_id, document_type_id, is_ready)"
                                    + " VALUES (?, ?, ?)")) {
                        for (Map.Entry<Integer, Boolean> entry : readyByDocTypeId.entrySet()) {
                            ins.setInt(1, studentId);
                            ins.setInt(2, entry.getKey());
                            ins.setInt(3, Boolean.TRUE.equals(entry.getValue()) ? 1 : 0);
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
            throw new DataAccessException("Failed to replace documents for student " + studentId, e);
        }
    }

    static DocumentType mapType(ResultSet rs) throws SQLException {
        return new DocumentType(
                rs.getInt("document_type_id"),
                rs.getString("doc_code"),
                rs.getString("doc_name"),
                rs.getString("description"));
    }

    static StudentDocument mapStudentDoc(ResultSet rs) throws SQLException {
        return new StudentDocument(
                rs.getInt("student_id"),
                rs.getInt("document_type_id"),
                rs.getInt("is_ready") != 0,
                Dates.parseTimestamp(rs.getString("updated_at")));
    }
}
