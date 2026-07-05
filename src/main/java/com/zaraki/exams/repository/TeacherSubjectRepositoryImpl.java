package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class TeacherSubjectRepositoryImpl implements ITeacherSubjectRepository {

    private static final Logger LOG = LoggerUtil.getLogger();
    private final DatabaseEngine db;

    public TeacherSubjectRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    public Set<Integer> findFormsByTeacherAndSubject(long userId, long subjectId) {
        Set<Integer> forms = new TreeSet<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "SELECT DISTINCT form FROM teacher_subjects WHERE user_id = ? AND subject_id = ? ORDER BY form")) {
            ps.setLong(1, userId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return forms;
    }

    public Set<String> findStreamsByTeacherAndSubjectAndForm(long userId, long subjectId, int form) {
        Set<String> streams = new TreeSet<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "SELECT stream FROM teacher_subjects WHERE user_id = ? AND subject_id = ? AND form = ? ORDER BY stream")) {
            ps.setLong(1, userId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return streams;
    }

    public List<Map<String, Object>> findByUserId(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT ts.id, s.subject_name, ts.form, ts.stream
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name, ts.form, ts.stream
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("subject_name", rs.getString("subject_name"));
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void insert(long userId, long subjectId, int form, String stream) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR IGNORE INTO teacher_subjects (user_id, subject_id, form, stream) VALUES (?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(long id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM teacher_subjects WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
