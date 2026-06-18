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

    public Map<Long, Double> getExamStudentTotals(long examId) {
        Map<Long, Double> map = new HashMap<>();
        String sql = "SELECT student_id, ROUND(SUM(score), 1) AS total FROM marks WHERE exam_id = ? GROUP BY student_id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                map.put(rs.getLong("student_id"), rs.getDouble("total"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get exam totals", e);
        }
        return map;
    }

    public Map<Long, Integer> getExamStudentRanks(long examId) {
        Map<Long, Double> totals = getExamStudentTotals(examId);
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<Long, Integer> ranks = new HashMap<>();
        int rank = 0, prevCount = 0;
        double prevTotal = Double.MAX_VALUE;
        for (var entry : sorted) {
            prevCount++;
            if (entry.getValue() < prevTotal) rank = prevCount;
            prevTotal = entry.getValue();
            ranks.put(entry.getKey(), rank);
        }
        return ranks;
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

    public record GradeDistribution(String subjectName, Map<String, Integer> gradeCounts) {}
    public record ExamComparison(long studentId, String admissionNumber, String fullName,
                                   String form, String stream,
                                   double exam1Total, double exam2Total, double difference,
                                   int exam1Pos, int exam2Pos, int posChange) {}
    public record StudentTrend(long examId, String examLabel, double totalPoints) {}

    public List<GradeDistribution> computeGradeDistribution(long examId) {
        String sql = """
            SELECT sub.subject_name, m.grade_achieved, COUNT(*) AS cnt
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ? AND m.grade_achieved IS NOT NULL
            GROUP BY sub.subject_name, m.grade_achieved
            ORDER BY sub.subject_name, m.grade_achieved
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
            while (rs.next()) {
                String subj = rs.getString("subject_name");
                String grade = rs.getString("grade_achieved");
                int cnt = rs.getInt("cnt");
                map.computeIfAbsent(subj, k -> new LinkedHashMap<>()).put(grade, cnt);
            }
            List<GradeDistribution> list = new ArrayList<>();
            for (var entry : map.entrySet())
                list.add(new GradeDistribution(entry.getKey(), entry.getValue()));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute grade distribution", e);
        }
    }

    public List<ExamComparison> compareExams(long exam1Id, long exam2Id) {
        String sql = """
            SELECT s.id, s.admission_number, s.full_name, s.form, s.stream,
                   COALESCE(e1.total, 0) AS exam1_total,
                   COALESCE(e2.total, 0) AS exam2_total
            FROM students s
            LEFT JOIN (SELECT student_id, ROUND(SUM(score), 1) AS total FROM marks WHERE exam_id = ? GROUP BY student_id) e1 ON e1.student_id = s.id
            LEFT JOIN (SELECT student_id, ROUND(SUM(score), 1) AS total FROM marks WHERE exam_id = ? GROUP BY student_id) e2 ON e2.student_id = s.id
            WHERE e1.total IS NOT NULL OR e2.total IS NOT NULL
            ORDER BY (COALESCE(e2.total, 0) - COALESCE(e1.total, 0)) DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, exam1Id);
            ps.setLong(2, exam2Id);
            ResultSet rs = ps.executeQuery();
            List<ExamComparison> list = new ArrayList<>();
            while (rs.next()) {
                double e1 = rs.getDouble("exam1_total");
                double e2 = rs.getDouble("exam2_total");
                list.add(new ExamComparison(
                    rs.getLong("id"), rs.getString("admission_number"), rs.getString("full_name"),
                    rs.getString("form"), rs.getString("stream"),
                    e1, e2, Math.round((e2 - e1) * 10.0) / 10.0
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compare exams", e);
        }
    }

    public long findPreviousExam(long examId) {
        String sql = "SELECT id FROM exams WHERE id < ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : -1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find previous exam", e);
        }
    }

    public List<StudentTrend> computeStudentTrend(long studentId) {
        String sql = """
            SELECT e.id, e.academic_year || ' ' || e.term || ' ' || e.exam_series AS label,
                   COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m
            JOIN exams e ON e.id = m.exam_id
            WHERE m.student_id = ?
            GROUP BY e.id, e.academic_year, e.term, e.exam_series
            ORDER BY e.id
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            List<StudentTrend> list = new ArrayList<>();
            while (rs.next())
                list.add(new StudentTrend(rs.getLong("id"), rs.getString("label"), rs.getDouble("total_points")));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute student trend", e);
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
