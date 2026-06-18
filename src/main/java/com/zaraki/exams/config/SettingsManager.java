package com.zaraki.exams.config;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;

public class SettingsManager {

    private final DatabaseEngine db;

    public SettingsManager() {
        this.db = DatabaseEngine.getInstance();
        ensureTable();
    }

    private void ensureTable() {
        try (Statement st = db.getConnection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT)");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('curriculum', 'SYSTEM_844')");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init settings", e);
        }
    }

    public CurriculumSystem getCurriculum() {
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM app_settings WHERE key = 'curriculum'")) {
            if (rs.next()) return CurriculumSystem.fromCode(rs.getString("value"));
            return CurriculumSystem.SYSTEM_844;
        } catch (SQLException e) {
            return CurriculumSystem.SYSTEM_844;
        }
    }

    public void setCurriculum(CurriculumSystem system) {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("INSERT OR REPLACE INTO app_settings (key, value) VALUES ('curriculum', ?)")) {
            ps.setString(1, system.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save curriculum setting", e);
        }
    }
}
