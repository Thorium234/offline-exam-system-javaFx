package com.zaraki.exams.repository;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;
import java.util.*;

public class GradingScaleRepository {

    private final DatabaseEngine db;

    public GradingScaleRepository() {
        this.db = DatabaseEngine.getInstance();
    }

    public List<Map<String, Object>> findAllWithSubject() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT gs.id, gs.minimum_mark, gs.maximum_mark, gs.grade, gs.points, gs.remarks,
                   COALESCE(sub.subject_name, '** Global **') AS subject_name
            FROM grading_scales gs
            LEFT JOIN subjects sub ON sub.id = gs.subject_id
            ORDER BY gs.subject_id IS NULL DESC, gs.points DESC
            """;
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("subject_name", rs.getString("subject_name"));
                row.put("minimum_mark", rs.getDouble("minimum_mark"));
                row.put("maximum_mark", rs.getDouble("maximum_mark"));
                row.put("grade", rs.getString("grade"));
                row.put("points", rs.getInt("points"));
                row.put("remarks", rs.getString("remarks"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Map<String, Object>> findAllSubjectsForCombo() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_name FROM subjects ORDER BY subject_name")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_name", rs.getString("subject_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void insert(Long subjectId, double min, double max, String grade, int points, String remarks) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?,?,?,?,?,?)")) {
            if (subjectId == null) ps.setNull(1, Types.INTEGER);
            else ps.setLong(1, subjectId);
            ps.setDouble(2, min);
            ps.setDouble(3, max);
            ps.setString(4, grade);
            ps.setInt(5, points);
            ps.setString(6, remarks);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteGlobal() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM grading_scales WHERE subject_id IS NULL")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertBatchGlobal(CurriculumSystem curriculum) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (CurriculumSystem.PresetGrade pg : curriculum.getPresetGrades()) {
                    ps.setDouble(1, pg.min());
                    ps.setDouble(2, pg.max());
                    ps.setString(3, pg.grade());
                    ps.setInt(4, pg.points());
                    ps.setString(5, pg.remarks());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
