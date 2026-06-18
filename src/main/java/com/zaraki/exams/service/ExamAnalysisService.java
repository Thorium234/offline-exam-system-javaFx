package com.zaraki.exams.service;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ExamAnalysisService {

    private final DatabaseEngine db;

    public ExamAnalysisService() {
        this.db = DatabaseEngine.getInstance();
    }

    public record SubjectMetrics(long subjectId, String subjectName, String department,
                                  double meanScore, String meanGrade,
                                  double stdDev, int subjectRank, int totalCandidates) {}

    public record StudentResult(long studentId, String admissionNumber, String fullName,
                                 String form, String stream,
                                 double totalMarks, int totalPoints, double meanPoints,
                                 String meanGrade, int classRank, int streamRank,
                                 int classSize, int streamSize) {}

    public List<SubjectMetrics> computeSubjectMetrics(long examId) {
        String sql = """
            SELECT
                sub.id,
                sub.subject_name,
                sub.department,
                ROUND(AVG(m.score), 1) AS mean_score,
                ROUND(AVG(m.score) * 1.0, 1) AS mean_for_grade,
                ROUND(AVG(m.score), 1) AS mean_display,
                CASE
                    WHEN AVG(m.score) >= 80 THEN 'A'
                    WHEN AVG(m.score) >= 75 THEN 'A-'
                    WHEN AVG(m.score) >= 70 THEN 'B+'
                    WHEN AVG(m.score) >= 65 THEN 'B'
                    WHEN AVG(m.score) >= 60 THEN 'B-'
                    WHEN AVG(m.score) >= 55 THEN 'C+'
                    WHEN AVG(m.score) >= 50 THEN 'C'
                    WHEN AVG(m.score) >= 45 THEN 'C-'
                    WHEN AVG(m.score) >= 40 THEN 'D+'
                    WHEN AVG(m.score) >= 35 THEN 'D'
                    WHEN AVG(m.score) >= 30 THEN 'D-'
                    ELSE 'E'
                END AS mean_grade,
                ROUND(SUM((m.score - sub_avg.avg_score) * (m.score - sub_avg.avg_score)) /
                    NULLIF(COUNT(m.score) - 1, 0), 1) AS variance,
                COUNT(m.score) AS candidates
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            JOIN (
                SELECT subject_id, AVG(score) AS avg_score
                FROM marks WHERE exam_id = ?
                GROUP BY subject_id
            ) sub_avg ON sub_avg.subject_id = m.subject_id
            WHERE m.exam_id = ?
            GROUP BY sub.id, sub.subject_name, sub.department
            ORDER BY mean_score DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, examId);
            ResultSet rs = ps.executeQuery();
            List<SubjectMetrics> list = new ArrayList<>();
            int rank = 0;
            double prevMean = Double.MAX_VALUE;
            while (rs.next()) {
                double mean = rs.getDouble("mean_score");
                if (mean < prevMean) rank = list.size() + 1;
                prevMean = mean;
                double variance = rs.getDouble("variance");
                double stdDev = Math.round(Math.sqrt(variance) * 10.0) / 10.0;
                list.add(new SubjectMetrics(
                    rs.getLong("id"),
                    rs.getString("subject_name"),
                    rs.getString("department"),
                    mean,
                    rs.getString("mean_grade"),
                    stdDev,
                    rank,
                    rs.getInt("candidates")
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute subject metrics", e);
        }
    }

    public List<StudentResult> computeClassRankings(long examId) {
        String sql = """
            SELECT
                s.id,
                s.admission_number,
                s.full_name,
                s.form,
                s.stream,
                ROUND(SUM(m.score), 1) AS total_marks,
                COALESCE(SUM(m.points_achieved), 0) AS total_points,
                ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points,
                CASE
                    WHEN AVG(m.points_achieved) >= 12 THEN 'A'
                    WHEN AVG(m.points_achieved) >= 11 THEN 'A-'
                    WHEN AVG(m.points_achieved) >= 10 THEN 'B+'
                    WHEN AVG(m.points_achieved) >= 9  THEN 'B'
                    WHEN AVG(m.points_achieved) >= 8  THEN 'B-'
                    WHEN AVG(m.points_achieved) >= 7  THEN 'C+'
                    WHEN AVG(m.points_achieved) >= 6  THEN 'C'
                    WHEN AVG(m.points_achieved) >= 5  THEN 'C-'
                    WHEN AVG(m.points_achieved) >= 4  THEN 'D+'
                    WHEN AVG(m.points_achieved) >= 3  THEN 'D'
                    WHEN AVG(m.points_achieved) >= 2  THEN 'D-'
                    ELSE 'E'
                END AS mean_grade
            FROM marks m
            JOIN students s ON s.id = m.student_id
            WHERE m.exam_id = ?
            GROUP BY s.id, s.admission_number, s.full_name, s.form, s.stream
            ORDER BY total_points DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();

            List<StudentResult> all = new ArrayList<>();
            int rank = 0;
            int prevPoints = Integer.MAX_VALUE;
            int total = 0;

            while (rs.next()) {
                total++;
                int pts = rs.getInt("total_points");
                if (pts < prevPoints) rank = total;
                prevPoints = pts;
                String studentStream = rs.getString("stream");
                all.add(new StudentResult(
                    rs.getLong("id"),
                    rs.getString("admission_number"),
                    rs.getString("full_name"),
                    rs.getString("form"),
                    studentStream,
                    rs.getDouble("total_marks"),
                    pts,
                    rs.getDouble("mean_points"),
                    rs.getString("mean_grade"),
                    rank,
                    0,
                    total,
                    0
                ));
            }

            Map<String, Long> streamSizes = all.stream()
                .collect(Collectors.groupingBy(s -> s.stream, Collectors.counting()));
            Map<String, Map<Integer, Integer>> streamRanks = computeStreamRanks(examId, all);
            List<StudentResult> updated = new ArrayList<>();
            for (StudentResult sr : all) {
                Map<Integer, Integer> sRankMap = streamRanks.getOrDefault(sr.stream, new HashMap<>());
                int streamRank = sRankMap.getOrDefault((int) sr.totalPoints, 1);
                int streamSize = streamSizes.getOrDefault(sr.stream, 0L).intValue();
                updated.add(new StudentResult(
                    sr.studentId, sr.admissionNumber, sr.fullName,
                    sr.form, sr.stream, sr.totalMarks, sr.totalPoints, sr.meanPoints,
                    sr.meanGrade, sr.classRank, streamRank, sr.classSize, streamSize
                ));
            }
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute class rankings", e);
        }
    }

    private Map<String, Map<Integer, Integer>> computeStreamRanks(long examId, List<StudentResult> all) {
        String sql = """
            SELECT s.stream, COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m
            JOIN students s ON s.id = m.student_id
            WHERE m.exam_id = ?
            GROUP BY s.id, s.stream
            ORDER BY s.stream, total_points DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            Map<String, Map<Integer, Integer>> result = new HashMap<>();
            Map<String, Integer> streamCounters = new HashMap<>();
            Map<String, Integer> streamPrevPoints = new HashMap<>();
            while (rs.next()) {
                String stream = rs.getString("stream");
                int pts = rs.getInt("total_points");
                streamCounters.put(stream, streamCounters.getOrDefault(stream, 0) + 1);
                int prev = streamPrevPoints.getOrDefault(stream, Integer.MAX_VALUE);
                if (pts < prev) {
                    result.computeIfAbsent(stream, k -> new HashMap<>())
                          .put(pts, streamCounters.get(stream));
                } else {
                    result.computeIfAbsent(stream, k -> new HashMap<>())
                          .put(pts, result.get(stream).getOrDefault(prev, streamCounters.get(stream)));
                }
                streamPrevPoints.put(stream, pts);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute stream ranks", e);
        }
    }

    public void autoGradeExam(long examId) {
        String fetchSql = """
            SELECT m.exam_id, m.student_id, m.subject_id, m.score
            FROM marks m WHERE m.exam_id = ? AND (m.grade_achieved IS NULL OR m.points_achieved IS NULL)
            """;
        String gradeSql = """
            SELECT grade, points FROM grading_scales
            WHERE (subject_id IS NULL OR subject_id = ?)
              AND ? BETWEEN minimum_mark AND maximum_mark
            ORDER BY subject_id NULLS LAST, points DESC
            LIMIT 1
            """;
        String updateSql = "UPDATE marks SET grade_achieved = ?, points_achieved = ? WHERE exam_id = ? AND student_id = ? AND subject_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement fetchPs = conn.prepareStatement(fetchSql);
             PreparedStatement gradePs = conn.prepareStatement(gradeSql);
             PreparedStatement updatePs = conn.prepareStatement(updateSql)) {

            fetchPs.setLong(1, examId);
            ResultSet rs = fetchPs.executeQuery();
            conn.setAutoCommit(false);

            try {
                while (rs.next()) {
                    long eId = rs.getLong("exam_id");
                    long sId = rs.getLong("student_id");
                    long subjId = rs.getLong("subject_id");
                    double score = rs.getDouble("score");

                    gradePs.setLong(1, subjId);
                    gradePs.setDouble(2, score);
                    ResultSet gr = gradePs.executeQuery();
                    String grade = null;
                    int points = 0;
                    if (gr.next()) {
                        grade = gr.getString("grade");
                        points = gr.getInt("points");
                    }

                    updatePs.setString(1, grade);
                    updatePs.setInt(2, points);
                    updatePs.setLong(3, eId);
                    updatePs.setLong(4, sId);
                    updatePs.setLong(5, subjId);
                    updatePs.addBatch();
                }
                updatePs.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to auto-grade exam", e);
        }
    }

    public String determineGradeAndPoints(double score, Long subjectId) {
        String sql = """
            SELECT grade, points FROM grading_scales
            WHERE (subject_id IS NULL OR subject_id = ?)
              AND ? BETWEEN minimum_mark AND maximum_mark
            ORDER BY subject_id NULLS LAST, points DESC
            LIMIT 1
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (subjectId != null) ps.setLong(1, subjectId);
            else ps.setNull(1, Types.INTEGER);
            ps.setDouble(2, score);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("grade") + "|" + rs.getInt("points");
            }
            return "E|0";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to determine grade", e);
        }
    }
}
