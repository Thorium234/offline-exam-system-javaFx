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
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('school_name', 'Kenya Secondary School')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('closing_date', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('opening_date', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('logo_path', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('stamp_path', '')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('best_of_n', '0')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_high', 'Excellent performance. Keep it up!')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_average', 'Good performance. Room for improvement.')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('remark_low', 'Needs more effort and focus.')");
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
        set("curriculum", system.name());
    }

    public String getSchoolName() { return get("school_name", "Kenya Secondary School"); }
    public void setSchoolName(String v) { set("school_name", v); }

    public String getClosingDate() { return get("closing_date", ""); }
    public void setClosingDate(String v) { set("closing_date", v); }

    public String getOpeningDate() { return get("opening_date", ""); }
    public void setOpeningDate(String v) { set("opening_date", v); }

    public String getLogoPath() { return get("logo_path", ""); }
    public void setLogoPath(String v) { set("logo_path", v); }

    public String getStampPath() { return get("stamp_path", ""); }
    public void setStampPath(String v) { set("stamp_path", v); }

    public String getSetting(String key, String def) {
        return get(key, def);
    }

    public void setSetting(String key, String value) {
        set(key, value);
    }

    private String get(String key, String def) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { String v = rs.getString("value"); return v != null ? v : def; }
            return def;
        } catch (SQLException e) { return def; }
    }

    private void set(String key, String value) {
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
