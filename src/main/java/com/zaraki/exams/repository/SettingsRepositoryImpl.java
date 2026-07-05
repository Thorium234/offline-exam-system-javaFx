package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.logging.Logger;

public class SettingsRepositoryImpl implements ISettingsRepository {

    private static final Logger LOG = LoggerUtil.getLogger();
    private final DatabaseEngine db;
    private static boolean tableEnsured = false;

    public SettingsRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
        ensureTable();
    }

    private void ensureTable() {
        if (tableEnsured) return;
        try (Statement st = db.getConnection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT)");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('curriculum', 'SYSTEM_844')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('school_name', 'Kenya Secondary School')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('closing_date', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('opening_date', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('logo_path', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('stamp_path', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('best_of_n', '0')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_high', 'Excellent performance. Keep it up!')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_average', 'Good performance. Room for improvement.')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_low', 'Needs more effort and focus.')");
            tableEnsured = true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init settings", e);
        }
    }

    public String get(String key, String def) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String v = rs.getString("value");
                return v != null ? v : def;
            }
            return def;
        } catch (SQLException e) {
            return def;
        }
    }

    public void set(String key, String value) {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save setting: " + key, e);
        }
    }
}
