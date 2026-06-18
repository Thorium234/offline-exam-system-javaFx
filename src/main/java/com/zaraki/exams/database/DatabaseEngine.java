package com.zaraki.exams.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseEngine {

    private static final String DB_URL = "jdbc:sqlite:exam_analysis.db";
    private static volatile DatabaseEngine instance;
    private final Connection connection;

    private DatabaseEngine() {
        try {
            this.connection = DriverManager.getConnection(DB_URL);
            initializePragmas();
            executeDDL();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public static DatabaseEngine getInstance() {
        if (instance == null) {
            synchronized (DatabaseEngine.class) {
                if (instance == null) {
                    instance = new DatabaseEngine();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void initializePragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
        }
    }

    private void executeDDL() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    admission_number TEXT    NOT NULL UNIQUE,
                    full_name       TEXT    NOT NULL,
                    form            INTEGER NOT NULL CHECK(form BETWEEN 1 AND 4),
                    stream          TEXT    NOT NULL,
                    status          TEXT    NOT NULL DEFAULT 'Active'
                );
                CREATE INDEX IF NOT EXISTS idx_students_admission ON students(admission_number);
                CREATE INDEX IF NOT EXISTS idx_students_form_stream ON students(form, stream);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS subjects (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject_code  TEXT    NOT NULL UNIQUE,
                    subject_name  TEXT    NOT NULL,
                    department    TEXT    NOT NULL,
                    grouping      TEXT    NOT NULL CHECK(grouping IN ('Compulsory', 'Elective'))
                );
                CREATE INDEX IF NOT EXISTS idx_subjects_code ON subjects(subject_code);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS grading_scales (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject_id  INTEGER,
                    minimum_mark REAL   NOT NULL,
                    maximum_mark REAL   NOT NULL,
                    grade       TEXT    NOT NULL,
                    points      INTEGER NOT NULL,
                    remarks     TEXT    NOT NULL,
                    FOREIGN KEY (subject_id) REFERENCES subjects(id)
                );
                CREATE INDEX IF NOT EXISTS idx_grading_subject ON grading_scales(subject_id);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exams (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    academic_year TEXT    NOT NULL,
                    term          TEXT    NOT NULL CHECK(term IN ('Term 1', 'Term 2', 'Term 3')),
                    exam_series   TEXT    NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_exams_year_term ON exams(academic_year, term);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS marks (
                    exam_id         INTEGER NOT NULL,
                    student_id      INTEGER NOT NULL,
                    subject_id      INTEGER NOT NULL,
                    score           REAL    NOT NULL,
                    grade_achieved  TEXT,
                    points_achieved INTEGER,
                    PRIMARY KEY (exam_id, student_id, subject_id),
                    FOREIGN KEY (exam_id)    REFERENCES exams(id),
                    FOREIGN KEY (student_id) REFERENCES students(id),
                    FOREIGN KEY (subject_id) REFERENCES subjects(id)
                );
                CREATE INDEX IF NOT EXISTS idx_marks_exam   ON marks(exam_id);
                CREATE INDEX IF NOT EXISTS idx_marks_student ON marks(student_id);
                CREATE INDEX IF NOT EXISTS idx_marks_exam_student ON marks(exam_id, student_id);
            """);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database connection", e);
        }
    }
}
