package com.zaraki.exams.database;

import com.zaraki.exams.auth.PasswordUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseEngine {

    private static final String DB_URL = "jdbc:sqlite:exam_analysis.db";
    private static volatile DatabaseEngine instance;
    private Connection connection;

    private DatabaseEngine() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(DB_URL);
            initializePragmas();
            executeDDL();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
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

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                initializePragmas();
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain database connection", e);
        }
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
            try { stmt.execute("ALTER TABLE students ADD COLUMN deallocated INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
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
                CREATE INDEX IF NOT EXISTS idx_grading_range ON grading_scales(minimum_mark, maximum_mark);
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exams (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    academic_year TEXT    NOT NULL,
                    term          TEXT    NOT NULL CHECK(term IN ('Term 1', 'Term 2', 'Term 3')),
                    exam_series   TEXT    NOT NULL,
                    released      INTEGER NOT NULL DEFAULT 0,
                    released_by   TEXT,
                    released_at   TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_exams_year_term ON exams(academic_year, term);
            """);
            try { stmt.execute("ALTER TABLE exams ADD COLUMN released INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE exams ADD COLUMN released_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE exams ADD COLUMN released_at TEXT"); } catch (SQLException ignored) {}

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exam_subjects (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    exam_id      INTEGER NOT NULL,
                    subject_id   INTEGER NOT NULL,
                    out_of       INTEGER NOT NULL DEFAULT 100,
                    uploaded_by  TEXT,
                    uploaded_at  TEXT,
                    published    INTEGER NOT NULL DEFAULT 0,
                    published_by TEXT,
                    published_at TEXT,
                    FOREIGN KEY (exam_id)    REFERENCES exams(id),
                    FOREIGN KEY (subject_id) REFERENCES subjects(id),
                    UNIQUE(exam_id, subject_id)
                );
                CREATE INDEX IF NOT EXISTS idx_exam_subjects_exam ON exam_subjects(exam_id);
            """);

            boolean usersExists = false;
            try (var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")) {
                usersExists = rs.next();
            }

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT    NOT NULL UNIQUE,
                    password_hash TEXT    NOT NULL,
                    salt          TEXT    NOT NULL,
                    full_name     TEXT    NOT NULL,
                    role          TEXT    NOT NULL DEFAULT 'teacher'
                );
            """);

            if (!usersExists) {
                String salt = PasswordUtils.generateSalt();
                String hash = PasswordUtils.hashPassword("admin", salt);
                try (var ups = connection.prepareStatement(
                        "INSERT OR IGNORE INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)")) {
                    ups.setString(1, "admin");
                    ups.setString(2, hash);
                    ups.setString(3, salt);
                    ups.setString(4, "Administrator");
                    ups.setString(5, "admin");
                    ups.executeUpdate();
                }
            }

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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS teacher_subjects (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id    INTEGER NOT NULL,
                    subject_id INTEGER NOT NULL,
                    form       INTEGER NOT NULL,
                    stream     TEXT    NOT NULL,
                    FOREIGN KEY (user_id)    REFERENCES users(id),
                    FOREIGN KEY (subject_id) REFERENCES subjects(id),
                    UNIQUE(user_id, subject_id, form, stream)
                );
                CREATE INDEX IF NOT EXISTS idx_teacher_subjects_user ON teacher_subjects(user_id);
            """);
        }
    }

    private static final java.util.Set<String> ALLOWED_FILTER_COLUMNS = java.util.Set.of("form", "stream", "");

    public static String validateFilterColumn(String col) {
        if (!ALLOWED_FILTER_COLUMNS.contains(col))
            throw new IllegalArgumentException("Invalid filter column: " + col);
        return col;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
