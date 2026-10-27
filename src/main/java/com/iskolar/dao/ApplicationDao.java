package com.iskolar.dao;

import com.iskolar.dto.ApplicationCard;
import com.iskolar.model.Application;
import com.iskolar.model.ApplicationStatus;
import com.iskolar.model.Scholarship;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for {@code applications} — the student's Kanban tracker. */
public class ApplicationDao {

    private static final String UPSERT_SQL = """
            INSERT INTO applications (student_id, scholarship_id, status, applied_date, notes)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(student_id, scholarship_id)
            DO UPDATE SET status = excluded.status,
                          applied_date = excluded.applied_date,
                          notes = excluded.notes,
                          updated_at = datetime('now')
            """;

    private final DataSource dataSource;

    public ApplicationDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** All tracker cards for a student, newest activity first. */
    public List<ApplicationCard> findCardsForStudent(int studentId) {
        String sql = """
                SELECT a.application_id, a.student_id, a.status, a.applied_date, a.updated_at, a.notes,
                       s.scholarship_id, s.created_by, s.title, s.provider_name, s.provider_type,
                       s.description, s.tuition_benefit, s.monthly_stipend, s.return_service_years,
                       s.application_url, s.open_date, s.deadline_date, s.is_active
                FROM applications a
                JOIN scholarships s ON s.scholarship_id = a.scholarship_id
                WHERE a.student_id = ?
                ORDER BY a.updated_at DESC
                """;
        List<ApplicationCard> cards = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cards.add(new ApplicationCard(mapApplication(rs), ScholarshipDao.mapScholarship(rs)));
                }
            }
            return cards;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load application cards for student " + studentId, e);
        }
    }

    public Optional<Application> findByStudentAndScholarship(int studentId, long scholarshipId) {
        String sql = "SELECT application_id, student_id, scholarship_id, status, applied_date, updated_at, notes"
                + " FROM applications WHERE student_id = ? AND scholarship_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setLong(2, scholarshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapApplication(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Failed to find application for student " + studentId + ", scholarship " + scholarshipId, e);
        }
    }

    public Optional<Application> findById(long applicationId) {
        String sql = "SELECT application_id, student_id, scholarship_id, status, applied_date, updated_at, notes"
                + " FROM applications WHERE application_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapApplication(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find application " + applicationId, e);
        }
    }

    /**
     * Bookmarks a scholarship or advances its Kanban status (upsert on the
     * {@code (student_id, scholarship_id)} unique key). Sets {@code applied_date}
     * on first entry into {@code APPLIED}; preserves it afterwards.
     *
     * @return the persisted application
     */
    public Application save(int studentId, long scholarshipId, ApplicationStatus status, String notes) {
        LocalDate appliedDate = resolveAppliedDate(studentId, scholarshipId, status);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {
            ps.setInt(1, studentId);
            ps.setLong(2, scholarshipId);
            ps.setString(3, status.name());
            ps.setString(4, Dates.toDate(appliedDate));
            ps.setString(5, notes);
            ps.executeUpdate();
            return findByStudentAndScholarship(studentId, scholarshipId).orElseThrow();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save application for scholarship " + scholarshipId, e);
        }
    }

    /** Advances an existing card's status (Kanban forward transition). */
    public void updateStatus(long applicationId, ApplicationStatus status) {
        String sql = """
                UPDATE applications
                SET status = ?,
                    applied_date = CASE WHEN ? = 'APPLIED'
                                       THEN COALESCE(applied_date, date('now'))
                                       ELSE applied_date END,
                    updated_at = datetime('now')
                WHERE application_id = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, status.name());
            ps.setLong(3, applicationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update application " + applicationId, e);
        }
    }

    public void delete(long applicationId) {
        String sql = "DELETE FROM applications WHERE application_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete application " + applicationId, e);
        }
    }

    private LocalDate resolveAppliedDate(int studentId, long scholarshipId, ApplicationStatus status) {
        Optional<Application> existing = findByStudentAndScholarship(studentId, scholarshipId);
        if (status == ApplicationStatus.APPLIED) {
            return existing.flatMap(a -> Optional.ofNullable(a.appliedDate())).orElse(LocalDate.now());
        }
        return existing.flatMap(a -> Optional.ofNullable(a.appliedDate())).orElse(null);
    }

    static Application mapApplication(ResultSet rs) throws SQLException {
        return new Application(
                rs.getLong("application_id"),
                rs.getInt("student_id"),
                rs.getLong("scholarship_id"),
                ApplicationStatus.fromDb(rs.getString("status")),
                Dates.parseDate(rs.getString("applied_date")),
                Dates.parseTimestamp(rs.getString("updated_at")),
                rs.getString("notes"));
    }
}
