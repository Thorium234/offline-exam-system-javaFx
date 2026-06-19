package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.Mark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MarksRepository {

    private static final String INSERT_SQL = """
        INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved)
        VALUES (?, ?, ?, ?, ?, ?)
    """;

    private final DatabaseEngine db;

    public MarksRepository() {
        this.db = DatabaseEngine.getInstance();
    }

    public void insert(Mark mark) {
        batchInsert(List.of(mark));
    }

    public void batchInsert(Collection<Mark> marks) {
        if (marks == null || marks.isEmpty()) return;

        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int count = 0;
                for (Mark mark : marks) {
                    ps.setLong(1, mark.getExamId());
                    ps.setLong(2, mark.getStudentId());
                    ps.setLong(3, mark.getSubjectId());
                    ps.setDouble(4, mark.getScore());
                    ps.setString(5, mark.getGradeAchieved());
                    ps.setObject(6, mark.getPointsAchieved() > 0 ? mark.getPointsAchieved() : null);
                    ps.addBatch();
                    count++;

                    if (count % 500 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Batch insert failed, transaction rolled back", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute batch insert", e);
        }
    }

    public List<Mark> findByExamId(long examId) {
        String sql = "SELECT exam_id, student_id, subject_id, score, grade_achieved, points_achieved " +
                     "FROM marks WHERE exam_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            var rs = ps.executeQuery();
            List<Mark> results = new java.util.ArrayList<>();
            while (rs.next()) {
                Mark m = new Mark();
                m.setExamId(rs.getLong("exam_id"));
                m.setStudentId(rs.getLong("student_id"));
                m.setSubjectId(rs.getLong("subject_id"));
                m.setScore(rs.getDouble("score"));
                m.setGradeAchieved(rs.getString("grade_achieved"));
                m.setPointsAchieved(rs.getInt("points_achieved"));
                results.add(m);
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query marks by exam", e);
        }
    }

    public Optional<Mark> findByExamStudentSubject(long examId, long studentId, long subjectId) {
        String sql = "SELECT exam_id, student_id, subject_id, score, grade_achieved, points_achieved " +
                     "FROM marks WHERE exam_id = ? AND student_id = ? AND subject_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ps.setLong(3, subjectId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                Mark m = new Mark();
                m.setExamId(rs.getLong("exam_id"));
                m.setStudentId(rs.getLong("student_id"));
                m.setSubjectId(rs.getLong("subject_id"));
                m.setScore(rs.getDouble("score"));
                m.setGradeAchieved(rs.getString("grade_achieved"));
                m.setPointsAchieved(rs.getInt("points_achieved"));
                return Optional.of(m);
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query mark", e);
        }
    }

    public void deleteByExam(long examId) {
        String sql = "DELETE FROM marks WHERE exam_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete marks for exam", e);
        }
    }
}
