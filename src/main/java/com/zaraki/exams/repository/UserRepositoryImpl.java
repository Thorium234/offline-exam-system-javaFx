package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class UserRepositoryImpl implements IUserRepository {

    private static final Logger LOG = LoggerUtil.getLogger();
    private final DatabaseEngine db;

    public UserRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    public Map<String, Object> findByUsername(String username) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "SELECT id, username, password_hash, salt, full_name, role FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("username", rs.getString("username"));
                row.put("password_hash", rs.getString("password_hash"));
                row.put("salt", rs.getString("salt"));
                row.put("full_name", rs.getString("full_name"));
                row.put("role", rs.getString("role"));
                return row;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, full_name, role FROM users ORDER BY full_name")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("username", rs.getString("username"));
                row.put("full_name", rs.getString("full_name"));
                row.put("role", rs.getString("role"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void insert(String username, String passwordHash, String salt, String fullName, String role) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "INSERT INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long userId) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "DELETE FROM users WHERE id = ? AND username != 'admin'")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long resolveUserId(String username) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public void changePassword(long userId, String newHash, String newSalt) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                 "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?")) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setLong(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
