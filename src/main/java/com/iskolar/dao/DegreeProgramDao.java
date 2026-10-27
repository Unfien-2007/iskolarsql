package com.iskolar.dao;

import com.iskolar.model.DegreeProgram;
import com.iskolar.model.FieldCategory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DAO for the {@code degree_programs} lookup table. */
public class DegreeProgramDao {

    private final DataSource dataSource;

    public DegreeProgramDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<DegreeProgram> findAll() {
        String sql = "SELECT program_id, program_code, program_name, field_category"
                + " FROM degree_programs ORDER BY field_category, program_name";
        List<DegreeProgram> programs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                programs.add(map(rs));
            }
            return programs;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load degree programs", e);
        }
    }

    public Optional<DegreeProgram> findById(int programId) {
        String sql = "SELECT program_id, program_code, program_name, field_category"
                + " FROM degree_programs WHERE program_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, programId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find degree program: " + programId, e);
        }
    }

    static DegreeProgram map(ResultSet rs) throws SQLException {
        return new DegreeProgram(
                rs.getInt("program_id"),
                rs.getString("program_code"),
                rs.getString("program_name"),
                FieldCategory.fromDb(rs.getString("field_category")));
    }
}
