package com.zaraki.exams;

import com.zaraki.exams.auth.PasswordUtils;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.database.InMemoryDbExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DatabaseTestBase {

    @RegisterExtension
    protected static InMemoryDbExtension dbExtension = new InMemoryDbExtension();

    protected DatabaseEngine db = DatabaseEngine.getInstance();

    @BeforeEach
    void setUp() {
        cleanAllTables();
    }

    protected Connection getConnection() {
        return db.getConnection();
    }

    protected void cleanAllTables() {
        InMemoryDbExtension.cleanAllTables();
    }

    protected long insertSubject(String code, String name, String department, String grouping) {
        String sql = "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, department);
            ps.setString(4, grouping);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected long insertStudent(String admission, String name, int form, String stream) {
        String sql = "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, admission);
            ps.setString(2, name);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected long insertExam(String year, String term, String series) {
        String sql = "INSERT INTO exams (academic_year, term, exam_series) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, year);
            ps.setString(2, term);
            ps.setString(3, series);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertMark(long examId, long studentId, long subjectId, double score) {
        insertMark(examId, studentId, subjectId, score, null, 0);
    }

    protected void insertMark(long examId, long studentId, long subjectId, double score,
                              String grade, int points) {
        String sql = "INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ps.setLong(3, subjectId);
            ps.setDouble(4, score);
            if (grade != null) {
                ps.setString(5, grade);
                ps.setInt(6, points);
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected long insertGradeScale(Long subjectId, double min, double max, String grade, int points, String remarks) {
        String sql = "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (subjectId == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setLong(1, subjectId);
            ps.setDouble(2, min);
            ps.setDouble(3, max);
            ps.setString(4, grade);
            ps.setInt(5, points);
            ps.setString(6, remarks);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected long insertUser(String username, String password, String fullName, String role) {
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        String sql = "INSERT INTO users (username, password_hash, salt, full_name, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, role);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertStream(int form, String stream) {
        String sql = "INSERT OR IGNORE INTO streams (form, stream) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertExamSubject(long examId, long subjectId, int outOf) {
        String sql = "INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ps.setInt(3, outOf);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertStudentSubject(long studentId, long subjectId) {
        String sql = "INSERT OR IGNORE INTO student_subjects (student_id, subject_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertStreamSubject(int form, String stream, long subjectId) {
        String sql = "INSERT OR IGNORE INTO stream_subjects (form, stream, subject_id) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.setLong(3, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertTeacherSubject(long userId, long subjectId, int form, String stream) {
        String sql = "INSERT OR IGNORE INTO teacher_subjects (user_id, subject_id, form, stream) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
