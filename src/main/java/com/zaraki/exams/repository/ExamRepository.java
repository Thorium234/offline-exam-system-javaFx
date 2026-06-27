package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;
import java.util.*;

public class ExamRepository {

    private final DatabaseEngine db;

    public ExamRepository() {
        this.db = DatabaseEngine.getInstance();
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT e.id, e.academic_year, e.term, e.exam_series,
                   COALESCE((SELECT SUM(COALESCE(es.out_of, 100)) FROM exam_subjects es WHERE es.exam_id = e.id), 0) AS max_marks
            FROM exams e ORDER BY e.id
            """;
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("academic_year", rs.getString("academic_year"));
                row.put("term", rs.getString("term"));
                row.put("exam_series", rs.getString("exam_series"));
                row.put("max_marks", rs.getInt("max_marks"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Map<String, Object>> findAllDesc() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("academic_year", rs.getString("academic_year"));
                row.put("term", rs.getString("term"));
                row.put("exam_series", rs.getString("exam_series"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void insert(String year, String term, String series) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)")) {
            ps.setString(1, year);
            ps.setString(2, term);
            ps.setString(3, series);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, academic_year, term, exam_series, released FROM exams WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("academic_year", rs.getString("academic_year"));
                row.put("term", rs.getString("term"));
                row.put("exam_series", rs.getString("exam_series"));
                row.put("released", rs.getInt("released"));
                return row;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isReleased(long examId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT released FROM exams WHERE id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("released") == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public void release(long examId, String releasedBy) {
        String sql = "UPDATE exams SET released = 1, released_by = ?, released_at = datetime('now') WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releasedBy);
            ps.setLong(2, examId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long getFirstExamId() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(id) FROM exams")) {
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    public long getPreviousExamId(long currentExamId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM exams WHERE id < ? ORDER BY id DESC LIMIT 1")) {
            ps.setLong(1, currentExamId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    public List<Long> findLatestIds(int limit) {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM exams ORDER BY id DESC LIMIT " + limit)) {
            while (rs.next()) ids.add(rs.getLong("id"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }

    public int getMaxMarks(long examId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(SUM(COALESCE(out_of, 100)), 0) FROM exam_subjects WHERE exam_id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public void update(long id, String academicYear, String term, String examSeries) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE exams SET academic_year = ?, term = ?, exam_series = ? WHERE id = ?")) {
            ps.setString(1, academicYear);
            ps.setString(2, term);
            ps.setString(3, examSeries);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update exam", e);
        }
    }

    public void delete(long id) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM marks WHERE exam_id = ?")) {
                    ps.setLong(1, id); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM exam_subjects WHERE exam_id = ?")) {
                    ps.setLong(1, id); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM exams WHERE id = ?")) {
                    ps.setLong(1, id); ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete exam", e);
        }
    }
}
