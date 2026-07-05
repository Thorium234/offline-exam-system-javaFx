package com.zaraki.exams.database;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class InMemoryDbExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private static boolean initialized = false;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (initialized) return;
        resetDatabaseEngineSingleton("jdbc:sqlite:");
        DatabaseEngine.getInstance();
        initialized = true;
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        cleanAllTables();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // no-op: keep the engine alive for the class
    }

    public static void cleanAllTables() {
        try (Connection conn = DatabaseEngine.getInstance().getConnection();
             Statement st = conn.createStatement()) {
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
            st.execute("PRAGMA foreign_keys = ON");
        } catch (Exception e) {
            throw new RuntimeException(e);
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

        Field tableEnsuredField;
        try {
            tableEnsuredField = Class.forName("com.zaraki.exams.repository.SettingsRepositoryImpl")
                .getDeclaredField("tableEnsured");
            tableEnsuredField.setAccessible(true);
            tableEnsuredField.set(null, false);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {}
    }
}
