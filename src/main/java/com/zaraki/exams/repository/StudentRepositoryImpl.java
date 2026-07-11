package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class StudentRepositoryImpl implements IStudentRepository {

    private static final Logger LOG = LoggerUtil.getLogger();
    private final DatabaseEngine db;

    public StudentRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    public List<Map<String, Object>> findAllActive() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, admission_number, full_name, form, stream FROM students WHERE deallocated = 0";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Map<String, Object>> search(String query, int limit, int offset) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, admission_number, full_name, form, stream FROM students WHERE deallocated = 0 AND (full_name LIKE ? OR admission_number LIKE ?) ORDER BY full_name LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + query + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public int searchCount(String query) {
        String sql = "SELECT COUNT(*) FROM students WHERE deallocated = 0 AND (full_name LIKE ? OR admission_number LIKE ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + query + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public void insert(String admission, String name, int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)")) {
            ps.setString(1, admission);
            ps.setString(2, name);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updatePhoto(long studentId, byte[] photoBytes) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE students SET photo = ? WHERE id = ?")) {
            ps.setBytes(1, photoBytes);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getPhoto(long studentId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT photo FROM students WHERE id = ?")) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes("photo");
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    public Set<Long> getEnrolledSubjectIds(long studentId) {
        Set<Long> enrolled = new HashSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT subject_id FROM student_subjects WHERE student_id = ?")) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) enrolled.add(rs.getLong("subject_id"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return enrolled;
    }

    public void saveSubjects(long studentId, Map<Long, Boolean> subjectSelections) {
        try (Connection conn = db.getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM student_subjects WHERE student_id = ?");
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT OR IGNORE INTO student_subjects (student_id, subject_id) VALUES (?,?)")) {
            del.setLong(1, studentId);
            del.executeUpdate();
            for (var entry : subjectSelections.entrySet()) {
                if (entry.getValue()) {
                    ins.setLong(1, studentId);
                    ins.setLong(2, entry.getKey());
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int countByFormStream(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM students WHERE form = ? AND stream = ? AND deallocated = 0")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public Map<String, Object> findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, admission_number, full_name, form, stream FROM students WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                return row;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findByFormStream(int form, String stream) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, admission_number, full_name FROM students WHERE form = ? AND stream = ? AND deallocated = 0 ORDER BY full_name";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public List<Map<String, Object>> findByFormStreamWithMarks(long examId, long subjectId, int form, String stream) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT s.id, s.admission_number, s.full_name,
                   m.score, m.grade_achieved, m.points_achieved,
                   m.status, m.teacher_comment, m.deviation
            FROM students s
            LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? AND m.subject_id = ?
            WHERE s.form = ? AND s.stream = ? AND s.deallocated = 0
            ORDER BY s.full_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                row.put("score", rs.getObject("score"));
                row.put("grade_achieved", rs.getString("grade_achieved"));
                row.put("points_achieved", rs.getObject("points_achieved"));
                row.put("status", rs.getString("status"));
                row.put("teacher_comment", rs.getString("teacher_comment"));
                row.put("deviation", rs.getObject("deviation"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public int countByFormStreamStatus(int form, String stream) {
        return countByFormStream(form, stream);
    }

    public void update(long id, String admissionNumber, String fullName, int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE students SET admission_number = ?, full_name = ?, form = ?, stream = ? WHERE id = ?")) {
            ps.setString(1, admissionNumber);
            ps.setString(2, fullName);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update student", e);
        }
    }

    public void deallocate(long studentId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE students SET deallocated = 1 WHERE id = ?")) {
            ps.setLong(1, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void restore(long studentId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE students SET deallocated = 0 WHERE id = ?")) {
            ps.setLong(1, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findAllDeallocated() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name, form, stream FROM students WHERE deallocated = 1")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("admission_number", rs.getString("admission_number"));
                row.put("full_name", rs.getString("full_name"));
                row.put("form", rs.getInt("form"));
                row.put("stream", rs.getString("stream"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public int countByFormStreamWithPrefix(String prefix, int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM students WHERE form = ? AND stream = ? AND deallocated = 0 AND admission_number LIKE ?")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ps.setString(3, prefix + "%");
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public void batchRestore(Set<Long> ids) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE students SET deallocated = 0 WHERE id = ?")) {
            for (Long id : ids) { ps.setLong(1, id); ps.addBatch(); }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void batchPermanentDelete(Set<Long> ids) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delMarks = conn.prepareStatement("DELETE FROM marks WHERE student_id = ?");
                 PreparedStatement delSubjects = conn.prepareStatement("DELETE FROM student_subjects WHERE student_id = ?");
                 PreparedStatement delStud = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                for (Long id : ids) {
                    delMarks.setLong(1, id); delMarks.addBatch();
                    delSubjects.setLong(1, id); delSubjects.addBatch();
                    delStud.setLong(1, id); delStud.addBatch();
                }
                delMarks.executeBatch();
                delSubjects.executeBatch();
                delStud.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
