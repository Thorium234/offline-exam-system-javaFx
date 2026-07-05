package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.CacheService;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class SubjectRepositoryImpl implements ISubjectRepository {

    private static final Logger LOG = LoggerUtil.getLogger();
    private static final String CACHE_KEY_ALL = "subjects_all";
    private final DatabaseEngine db;

    public SubjectRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> cached = CacheService.get(CACHE_KEY_ALL);
        if (cached != null) return cached;
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_code, subject_name, department, grouping FROM subjects ORDER BY subject_name")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_code", rs.getString("subject_code"));
                row.put("subject_name", rs.getString("subject_name"));
                row.put("department", rs.getString("department"));
                row.put("grouping", rs.getString("grouping"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        CacheService.put(CACHE_KEY_ALL, list);
        return list;
    }

    public List<Map<String, Object>> findAllSimple() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_code, subject_name FROM subjects ORDER BY subject_name")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_code", rs.getString("subject_code"));
                row.put("subject_name", rs.getString("subject_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void insert(String code, String name, String department, String grouping) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, department);
            ps.setString(4, grouping);
            ps.executeUpdate();
            CacheService.remove(CACHE_KEY_ALL);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByCode(String code) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM subjects WHERE subject_code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
            CacheService.remove(CACHE_KEY_ALL);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName(long subjectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT subject_name FROM subjects WHERE id = ?")) {
            ps.setLong(1, subjectId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("subject_name") : "Subject";
        } catch (SQLException e) {
            return "Subject";
        }
    }

    public Map<String, Object> findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, subject_code, subject_name, department, grouping FROM subjects WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_code", rs.getString("subject_code"));
                row.put("subject_name", rs.getString("subject_name"));
                row.put("department", rs.getString("department"));
                row.put("grouping", rs.getString("grouping"));
                return row;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findByTeacher(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT s.id, s.subject_code, s.subject_name
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_code", rs.getString("subject_code"));
                row.put("subject_name", rs.getString("subject_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Map<String, Object>> findByFormStreamWithMarksCount(long examId, int form, String stream) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT sub.id, sub.subject_code, sub.subject_name, sub.department,
                   COALESCE((SELECT es.out_of FROM exam_subjects es WHERE es.exam_id = ? AND es.subject_id = sub.id), 100) AS out_of,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = sub.id) AS mark_count
            FROM subjects sub
            WHERE sub.id IN (SELECT ss.subject_id FROM stream_subjects ss WHERE ss.form = ? AND ss.stream = ?)
               OR NOT EXISTS (SELECT 1 FROM stream_subjects WHERE form = ? AND stream = ?)
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, examId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.setInt(5, form);
            ps.setString(6, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_code", rs.getString("subject_code"));
                row.put("subject_name", rs.getString("subject_name"));
                row.put("department", rs.getString("department"));
                row.put("out_of", rs.getInt("out_of"));
                row.put("mark_count", rs.getInt("mark_count"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }
}
