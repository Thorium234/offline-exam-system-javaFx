package com.zaraki.exams.database;

import com.zaraki.exams.auth.PasswordUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class InMemoryDbExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private static Path tempDbPath;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Use a temp file per test class to avoid in-memory SQLite connection issues
        tempDbPath = Files.createTempFile("exam_test_", ".db");
        Files.deleteIfExists(tempDbPath); // fresh start
        String dbUrl = "jdbc:sqlite:" + tempDbPath.toAbsolutePath();
        resetDatabaseEngineSingleton(dbUrl);
        DatabaseEngine.getInstance();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        cleanAllTables();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (tempDbPath != null) {
            try { Files.deleteIfExists(tempDbPath); } catch (IOException ignored) {}
            tempDbPath = null;
        }
    }

    public static void cleanAllTables() {
        try {
            Connection conn = DatabaseEngine.getInstance().getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF");
                st.execute("DELETE FROM marks");
                st.execute("DELETE FROM exam_subjects");
                st.execute("DELETE FROM grading_scales");
                st.execute("DELETE FROM student_subjects");
                st.execute("DELETE FROM stream_subjects");
                st.execute("DELETE FROM teacher_subjects");
                st.execute("DELETE FROM exams");
                st.execute("DELETE FROM subjects");
                st.execute("DELETE FROM students");
                st.execute("DELETE FROM streams");
                st.execute("DELETE FROM users");
                st.execute("DELETE FROM app_settings");
                st.execute("DELETE FROM grading_system_entries");
                st.execute("DELETE FROM grading_systems");
                st.execute("DELETE FROM ranking_profile_weights");
                st.execute("DELETE FROM ranking_profiles");
                st.execute("DELETE FROM sqlite_sequence");
                st.execute("PRAGMA foreign_keys = ON");
            }
            // Re-seed structural defaults
            reSeedDefaults();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void reSeedDefaults() throws SQLException {
        Connection conn = DatabaseEngine.getInstance().getConnection();

        // Re-seed admin user
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("admin", salt);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)")) {
            ps.setString(1, "admin");
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, "Administrator");
            ps.setString(5, "admin");
            ps.executeUpdate();
        }

        // Re-seed default grading scales (12 rows, 8-4-4 system)
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL,?,?,?,?,?)")) {
            Object[][] grades = {
                {80.0, 100.0, "A",  12, "Excellent"},
                {75.0, 79.0,  "A-", 11, "Very Good"},
                {70.0, 74.0,  "B+", 10, "Good"},
                {65.0, 69.0,  "B",  9,  "Good"},
                {60.0, 64.0,  "B-", 8,  "Fairly Good"},
                {55.0, 59.0,  "C+", 7,  "Average"},
                {50.0, 54.0,  "C",  6,  "Average"},
                {45.0, 49.0,  "C-", 5,  "Below Average"},
                {40.0, 44.0,  "D+", 4,  "Below Average"},
                {35.0, 39.0,  "D",  3,  "Weak"},
                {30.0, 34.0,  "D-", 2,  "Weak"},
                {0.0,  29.0,  "E",  1,  "Poor"}
            };
            for (Object[] g : grades) {
                ps.setDouble(1, (double) g[0]);
                ps.setDouble(2, (double) g[1]);
                ps.setString(3, (String) g[2]);
                ps.setInt(4, (int) g[3]);
                ps.setString(5, (String) g[4]);
                ps.executeUpdate();
            }
        }

        // Re-seed default app_settings
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('curriculum', 'SYSTEM_844')");
            st.execute("INSERT OR IGNORE INTO app_settings (key, value) VALUES ('school_name', 'Kenya Secondary School')");
        }
    }

    @SuppressWarnings("unchecked")
    private static void resetDatabaseEngineSingleton(String dbUrl) throws Exception {
        Field instanceField = DatabaseEngine.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field dbUrlField = DatabaseEngine.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        dbUrlField.set(null, dbUrl);

        Field holderField = DatabaseEngine.class.getDeclaredField("connectionHolder");
        holderField.setAccessible(true);
        ThreadLocal<Connection> holder = (ThreadLocal<Connection>) holderField.get(null);
        Connection oldConn = holder.get();
        if (oldConn != null) {
            try { oldConn.close(); } catch (SQLException ignored) {}
        }
        holder.remove();
    }
}
