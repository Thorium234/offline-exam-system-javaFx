package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;
import java.util.*;

public class StreamRepository {

    private final DatabaseEngine db;

    public StreamRepository() {
        this.db = DatabaseEngine.getInstance();
    }

    public List<Map<String, Object>> findAllWithStudentCount() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT st.form, st.stream,
                   (SELECT COUNT(*) FROM students s WHERE s.form = st.form AND s.stream = st.stream AND s.deallocated = 0) AS cnt
            FROM streams st ORDER BY st.form, st.stream
            """;
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                row.put("cnt", rs.getInt("cnt"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public Set<String> findAllNames() {
        Set<String> names = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) names.add(rs.getString("stream"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return names;
    }

    public void insert(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO streams (form, stream) VALUES (?, ?)")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM streams WHERE form = ? AND stream = ?")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateStudentsStreamToGeneral(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE students SET stream = 'General' WHERE form = ? AND stream = ?")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Integer> findAllForms() {
        Set<Integer> forms = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM streams ORDER BY form")) {
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return forms;
    }

    public Set<Integer> findAllDistinctForms() {
        return findAllForms();
    }
}
