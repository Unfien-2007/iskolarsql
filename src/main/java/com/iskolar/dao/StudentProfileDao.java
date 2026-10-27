package com.iskolar.dao;

import com.iskolar.model.SchoolType;
import com.iskolar.model.Sex;
import com.iskolar.model.StudentProfile;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** DAO for {@code student_profiles} (the wizard's Steps 1-3 aggregate). */
public class StudentProfileDao {

    private static final String BASE_COLUMNS = """
            student_id, user_id, first_name, last_name, birthdate, sex, province,
            city_municipality, school_name, school_type, program_id, year_level,
            gwa_percentage, has_failing_grade, annual_family_income, household_size
            """;

    private static final String INSERT_SQL = """
            INSERT INTO student_profiles (user_id, first_name, last_name, birthdate, sex, province,
                                          city_municipality, school_name, school_type, program_id, year_level,
                                          gwa_percentage, has_failing_grade, annual_family_income, household_size)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE student_profiles SET first_name = ?, last_name = ?, birthdate = ?, sex = ?,
                   province = ?, city_municipality = ?, school_name = ?, school_type = ?,
                   program_id = ?, year_level = ?, gwa_percentage = ?, has_failing_grade = ?,
                   annual_family_income = ?, household_size = ?
            WHERE student_id = ?
            """;

    private final DataSource dataSource;

    public StudentProfileDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<StudentProfile> findByUserId(long userId) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM student_profiles WHERE user_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find profile for user: " + userId, e);
        }
    }

    public Optional<StudentProfile> findById(int studentId) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM student_profiles WHERE student_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find profile: " + studentId, e);
        }
    }

    /**
     * Upserts the profile. {@code studentId == 0} inserts a new row (user_id
     * must be set); otherwise the existing row is updated in place.
     *
     * @return the persisted profile (with generated id when inserting)
     */
    public StudentProfile save(StudentProfile profile) {
        if (profile.studentId() == 0) {
            return insert(profile);
        }
        return update(profile);
    }

    private StudentProfile insert(StudentProfile p) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, p.userId());
            bindFields(ps, p, 2);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated key returned for new profile");
                }
                int id = keys.getInt(1);
                return new StudentProfile(id, p.userId(), p.firstName(), p.lastName(), p.birthdate(),
                        p.sex(), p.province(), p.cityMunicipality(), p.schoolName(), p.schoolType(),
                        p.programId(), p.yearLevel(), p.gwaPercentage(), p.hasFailingGrade(),
                        p.annualFamilyIncome(), p.householdSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert profile for user " + p.userId(), e);
        }
    }

    private StudentProfile update(StudentProfile p) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE_SQL)) {
            bindFields(ps, p, 1);
            ps.setInt(15, p.studentId());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DataAccessException("No profile with id " + p.studentId() + " to update");
            }
            return p;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update profile " + p.studentId(), e);
        }
    }

    /**
     * Binds columns {@code start}..{@code start+13}: the 14 profile fields.
     * INSERT calls with {@code start = 2} (user_id occupies index 1);
     * UPDATE calls with {@code start = 1} (student_id occupies index 15).
     */
    private static void bindFields(PreparedStatement ps, StudentProfile p, int start) throws SQLException {
        int i = start;
        ps.setString(i++, p.firstName());
        ps.setString(i++, p.lastName());
        ps.setString(i++, Dates.toDate(p.birthdate()));
        ps.setString(i++, p.sex() == null ? null : p.sex().name());
        ps.setString(i++, p.province());
        ps.setString(i++, p.cityMunicipality());
        ps.setString(i++, p.schoolName());
        ps.setString(i++, p.schoolType() == null ? null : p.schoolType().name());
        if (p.programId() == null) {
            ps.setNull(i++, java.sql.Types.INTEGER);
        } else {
            ps.setInt(i++, p.programId());
        }
        if (p.yearLevel() == null) {
            ps.setNull(i++, java.sql.Types.INTEGER);
        } else {
            ps.setInt(i++, p.yearLevel());
        }
        ps.setDouble(i++, p.gwaPercentage());
        ps.setInt(i++, p.hasFailingGrade() ? 1 : 0);
        ps.setBigDecimal(i++, p.annualFamilyIncome());
        if (p.householdSize() == null) {
            ps.setNull(i, java.sql.Types.INTEGER);
        } else {
            ps.setInt(i, p.householdSize());
        }
    }

    static StudentProfile map(ResultSet rs) throws SQLException {
        return new StudentProfile(
                rs.getInt("student_id"),
                rs.getLong("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                Dates.parseDate(rs.getString("birthdate")),
                Sex.fromDb(rs.getString("sex")),
                rs.getString("province"),
                rs.getString("city_municipality"),
                rs.getString("school_name"),
                SchoolType.fromDb(rs.getString("school_type")),
                (Integer) rs.getObject("program_id"),
                (Integer) rs.getObject("year_level"),
                rs.getDouble("gwa_percentage"),
                rs.getInt("has_failing_grade") != 0,
                rs.getBigDecimal("annual_family_income"),
                (Integer) rs.getObject("household_size"));
    }
}
