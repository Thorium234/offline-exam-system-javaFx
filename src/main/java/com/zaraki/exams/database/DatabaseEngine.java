package com.zaraki.exams.database;

import com.zaraki.exams.auth.PasswordUtils;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseEngine {

    private static final String DB_URL = "jdbc:sqlite:exam_analysis.db";
    private static volatile DatabaseEngine instance;

    private DatabaseEngine() {
        try {
            Class.forName("org.sqlite.JDBC");
            // Run DDL once on the initial (FX thread) connection
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                }
                executeDDL(conn);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Best-effort close for the shutdown hook's own thread-local
                try {
                    Connection c = connectionHolder.get();
                    if (c != null && !c.isClosed()) c.close();
                } catch (SQLException ignored) {}
                connectionHolder.remove();
            }));
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

    private static final ThreadLocal<Connection> connectionHolder = ThreadLocal.withInitial(() -> {
        try {
            Connection conn = DriverManager.getConnection(DB_URL);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                stmt.execute("PRAGMA journal_mode = WAL;");
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database connection", e);
        }
    });

    public Connection getConnection() {
        Connection conn = connectionHolder.get();
        try {
            if (conn.isClosed()) {
                Connection newConn = DriverManager.getConnection(DB_URL);
                try (Statement stmt = newConn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                }
                connectionHolder.set(newConn);
                return newConn;
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain database connection", e);
        }
    }

    private void executeDDL(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

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
            try { stmt.execute("ALTER TABLE students ADD COLUMN photo BLOB"); } catch (SQLException ignored) {}
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS student_subjects (
                    student_id INTEGER NOT NULL,
                    subject_id INTEGER NOT NULL,
                    PRIMARY KEY (student_id, subject_id),
                    FOREIGN KEY (student_id) REFERENCES students(id),
                    FOREIGN KEY (subject_id) REFERENCES subjects(id)
                );
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stream_subjects (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    form       INTEGER NOT NULL,
                    stream     TEXT    NOT NULL,
                    subject_id INTEGER NOT NULL,
                    FOREIGN KEY (subject_id) REFERENCES subjects(id),
                    UNIQUE(form, stream, subject_id)
                );
                CREATE INDEX IF NOT EXISTS idx_stream_subjects_form_stream ON stream_subjects(form, stream);
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
                CREATE TABLE IF NOT EXISTS streams (
                    id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    form   INTEGER NOT NULL CHECK(form BETWEEN 1 AND 4),
                    stream TEXT    NOT NULL,
                    UNIQUE(form, stream)
                );
            """);
            try { stmt.executeUpdate(
                    "INSERT OR IGNORE INTO streams (form, stream) SELECT DISTINCT form, stream FROM students");
            } catch (SQLException ignored) {}

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

            boolean adminExists;
            try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username='admin'")) {
                adminExists = rs.next() && rs.getInt(1) > 0;
            }
            LoggerUtil.info("Admin user exists in DB: " + adminExists);

            if (!adminExists) {
                String salt = PasswordUtils.generateSalt();
                String hash = PasswordUtils.hashPassword("admin", salt);
                try (var ups = conn.prepareStatement(
                        "INSERT OR IGNORE INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)")) {
                    ups.setString(1, "admin");
                    ups.setString(2, hash);
                    ups.setString(3, salt);
                    ups.setString(4, "Administrator");
                    ups.setString(5, "admin");
                    int rows = ups.executeUpdate();
                    LoggerUtil.info("Admin user seeded (rows affected: " + rows + ")");
                }
            } else {
                String admSalt;
                String admHash;
                try (var rs = stmt.executeQuery("SELECT salt, password_hash FROM users WHERE username='admin'")) {
                    if (rs.next()) {
                        admSalt = rs.getString("salt");
                        admHash = rs.getString("password_hash");
                    } else {
                        admSalt = null;
                        admHash = null;
                    }
                }
                if (admSalt != null && admHash != null) {
                    boolean matches = PasswordUtils.verify("admin", admSalt, admHash);
                    LoggerUtil.info("Admin password matches 'admin': " + matches);
                    if (!matches) {
                        LoggerUtil.warn("Admin password does NOT match 'admin' — force-resetting");
                        String newSalt = PasswordUtils.generateSalt();
                        String newHash = PasswordUtils.hashPassword("admin", newSalt);
                        try (var ups = conn.prepareStatement(
                                "UPDATE users SET password_hash=?, salt=? WHERE username='admin'")) {
                            ups.setString(1, newHash);
                            ups.setString(2, newSalt);
                            int rows = ups.executeUpdate();
                            LoggerUtil.info("Admin password force-reset (rows affected: " + rows + ")");
                        }
                    }
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
            try { stmt.execute("ALTER TABLE marks ADD COLUMN status TEXT NOT NULL DEFAULT 'P'"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE marks ADD COLUMN teacher_comment TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE marks ADD COLUMN teacher_name TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE marks ADD COLUMN deviation REAL"); } catch (SQLException ignored) {}

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
}
