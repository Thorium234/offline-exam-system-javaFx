package com.zaraki.exams;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.Connection;
import java.sql.Statement;

public class TestDbConfig {

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        DatabaseEngine.getInstance();
        initialized = true;
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
            st.execute("PRAGMA foreign_keys = ON");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
