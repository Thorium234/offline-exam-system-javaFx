package com.zaraki.exams.service;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.GradingSystem;
import com.zaraki.exams.model.GradingSystemEntry;
import com.zaraki.exams.model.RankingProfile;
import com.zaraki.exams.model.RankingProfileWeight;
import com.zaraki.exams.repository.GradingSystemRepositoryImpl;
import com.zaraki.exams.repository.IGradingSystemRepository;
import com.zaraki.exams.repository.IRankingProfileRepository;
import com.zaraki.exams.repository.RankingProfileRepositoryImpl;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ExamAnalysisServiceImpl implements IExamAnalysisService {

    private final DatabaseEngine db;
    private final IGradingSystemRepository gradingSystemRepo;
    private final IRankingProfileRepository rankingProfileRepo;

    public ExamAnalysisServiceImpl() {
        this.db = DatabaseEngine.getInstance();
        this.gradingSystemRepo = new GradingSystemRepositoryImpl();
        this.rankingProfileRepo = new RankingProfileRepositoryImpl();
    }

    /**
     * Resolves the grade and points for a normalized score.
     * Uses active grading_systems if available, falls back to legacy grading_scales.
     */
    private String resolveGradeAndPoints(double normalizedScore, Long subjectId) {
        GradingSystem activeSystem = gradingSystemRepo.findActive();
        if (activeSystem != null) {
            List<GradingSystemEntry> entries = gradingSystemRepo.findEntriesBySystem(activeSystem.getId());
            for (GradingSystemEntry e : entries) {
                if (e.getSubjectId() == null || e.getSubjectId().equals(subjectId)) {
                    if (normalizedScore >= e.getMinimumMark() && normalizedScore <= e.getMaximumMark()) {
                        return e.getGrade() + "|" + e.getPoints();
                    }
                }
            }
            return "E|0";
        }
        return null; // signal to use legacy
    }

    /**
     * Resolves the grade letter for a mean-points value.
     * Uses active grading_systems if available, falls back to legacy grading_scales.
     */
    private String resolveMeanPointsToGrade(double meanPoints) {
        GradingSystem activeSystem = gradingSystemRepo.findActive();
        if (activeSystem != null) {
            List<GradingSystemEntry> entries = gradingSystemRepo.findEntriesBySystem(activeSystem.getId());
            String bestGrade = "E";
            for (GradingSystemEntry e : entries) {
                if (e.getSubjectId() == null && e.getPoints() <= meanPoints && e.getPoints() > 0) {
                    if (bestGrade.equals("E") || e.getPoints() > 0) {
                        bestGrade = e.getGrade();
                    }
                }
            }
            return bestGrade;
        }
        return null; // signal to use legacy
    }

    /**
     * Gets the active ranking profile, or null if none is active.
     */
    private RankingProfile getActiveRankingProfile() {
        return rankingProfileRepo.findActive();
    }

    /**
     * Computes a student's total points using the active ranking profile.
     * Falls back to simple sum if no profile is active.
     */
    private double computeWeightedTotal(long studentId, long examId, RankingProfile profile) {
        if (profile == null) {
            return computeSimpleTotalPoints(studentId, examId);
        }
        return switch (profile.getRankingMethod()) {
            case RankingProfile.METHOD_WEIGHTED_SUBJECTS -> computeWeightedSubjectsTotal(studentId, examId, profile);
            case RankingProfile.METHOD_BEST_OF_N -> computeBestOfNTotal(studentId, examId, profile.getBestOfN());
            default -> computeSimpleTotalPoints(studentId, examId);
        };
    }

    private double computeSimpleTotalPoints(long studentId, long examId) {
        String sql = "SELECT COALESCE(SUM(points_achieved), 0) AS total FROM marks "
            + "WHERE student_id = ? AND exam_id = ? AND (status IS NULL OR status = 'P')";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble("total") : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private double computeWeightedSubjectsTotal(long studentId, long examId, RankingProfile profile) {
        List<RankingProfileWeight> weights = rankingProfileRepo.findWeights(profile.getId());
        if (weights.isEmpty()) return computeSimpleTotalPoints(studentId, examId);

        Map<Long, Double> weightMap = new HashMap<>();
        for (RankingProfileWeight w : weights) {
            weightMap.put(w.getSubjectId(), w.getWeight());
        }

        String sql = "SELECT subject_id, points_achieved FROM marks "
            + "WHERE student_id = ? AND exam_id = ? AND (status IS NULL OR status = 'P')";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, examId);
            ResultSet rs = ps.executeQuery();
            double total = 0;
            while (rs.next()) {
                long subjId = rs.getLong("subject_id");
                int pts = rs.getInt("points_achieved");
                double weight = weightMap.getOrDefault(subjId, 1.0);
                total += pts * weight;
            }
            return Math.round(total * 10.0) / 10.0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private double computeBestOfNTotal(long studentId, long examId, int n) {
        if (n <= 0) return computeSimpleTotalPoints(studentId, examId);
        String sql = "SELECT points_achieved FROM marks WHERE student_id = ? AND exam_id = ? "
            + "AND (status IS NULL OR status = 'P') AND points_achieved IS NOT NULL "
            + "ORDER BY points_achieved DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, examId);
            ps.setInt(3, n);
            ResultSet rs = ps.executeQuery();
            int total = 0;
            while (rs.next()) total += rs.getInt(1);
            return total;
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public List<SubjectMetrics> computeSubjectMetrics(long examId) {
        String sql = """
            WITH subject_avgs AS (
                SELECT subject_id, AVG(score) AS avg_score
                FROM marks
                WHERE exam_id = ? AND (status IS NULL OR status = 'P')
                GROUP BY subject_id
            )
            SELECT
                sub.id,
                sub.subject_name,
                sub.department,
                ROUND(AVG(m.score), 1) AS mean_score,
                ROUND(SQRT(SUM((m.score - sa.avg_score) * (m.score - sa.avg_score)) /
                    NULLIF(COUNT(m.score) - 1, 0)), 1) AS std_dev,
                COUNT(m.score) AS candidates
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            JOIN subject_avgs sa ON sa.subject_id = m.subject_id
            WHERE m.exam_id = ? AND (m.status IS NULL OR m.status = 'P')
            GROUP BY sub.id, sub.subject_name, sub.department
            ORDER BY mean_score DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, examId);
            ResultSet rs = ps.executeQuery();
            // Collect raw data first (avoid nested queries on same connection)
            List<Long> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<String> depts = new ArrayList<>();
            List<Double> means = new ArrayList<>();
            List<Double> devs = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            List<Integer> ranks = new ArrayList<>();
            int rank = 0;
            double prevMean = Double.MAX_VALUE;
            while (rs.next()) {
                double mean = rs.getDouble("mean_score");
                if (mean < prevMean) rank = ids.size() + 1;
                prevMean = mean;
                ids.add(rs.getLong("id"));
                names.add(rs.getString("subject_name"));
                depts.add(rs.getString("department"));
                means.add(mean);
                devs.add(rs.getDouble("std_dev"));
                counts.add(rs.getInt("candidates"));
                ranks.add(rank);
            }
            rs.close();
            ps.close();
            List<SubjectMetrics> list = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String gp = determineGradeAndPoints(means.get(i), ids.get(i), examId);
                String meanGrade = gp.split("\\|")[0];
                list.add(new SubjectMetrics(
                    ids.get(i), names.get(i), depts.get(i),
                    means.get(i), meanGrade, devs.get(i), ranks.get(i), counts.get(i)
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute subject metrics", e);
        }
    }

    @Override
    public List<StudentResult> computeClassRankings(long examId) {
        RankingProfile activeProfile = getActiveRankingProfile();

        String sql = """
            SELECT
                s.id,
                s.admission_number,
                s.full_name,
                s.form,
                s.stream,
                ROUND(SUM(m.score), 1) AS total_marks,
                COALESCE(SUM(m.points_achieved), 0) AS total_points,
                ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points
            FROM marks m
            JOIN students s ON s.id = m.student_id
            WHERE m.exam_id = ? AND (m.status IS NULL OR m.status = 'P')
            GROUP BY s.id, s.admission_number, s.full_name, s.form, s.stream
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();

            // Collect all students first, then compute weighted totals
            record RawStudent(long id, String adm, String name, String form, String stream,
                              double totalMarks, int rawTotalPoints, double meanPts) {}
            List<RawStudent> rawStudents = new ArrayList<>();
            while (rs.next()) {
                rawStudents.add(new RawStudent(
                    rs.getLong("id"),
                    rs.getString("admission_number"),
                    rs.getString("full_name"),
                    rs.getString("form"),
                    rs.getString("stream"),
                    rs.getDouble("total_marks"),
                    rs.getInt("total_points"),
                    rs.getDouble("mean_points")
                ));
            }
            rs.close();
            ps.close();

            // Compute effective totals using ranking profile
            List<StudentResult> all = new ArrayList<>();
            List<Double> effectiveTotals = new ArrayList<>();
            for (RawStudent raw : rawStudents) {
                double effectiveTotal;
                if (activeProfile != null) {
                    effectiveTotal = computeWeightedTotal(raw.id(), examId, activeProfile);
                } else {
                    effectiveTotal = raw.rawTotalPoints();
                }
                effectiveTotals.add(effectiveTotal);
                all.add(new StudentResult(
                    raw.id(), raw.adm(), raw.name(), raw.form(), raw.stream(),
                    raw.totalMarks(), (int) effectiveTotal, raw.meanPts(),
                    null, 0, 0, 0, 0
                ));
            }

            // Sort by effective total descending and assign ranks
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) indices.add(i);
            indices.sort((a, b) -> Double.compare(effectiveTotals.get(b), effectiveTotals.get(a)));

            List<StudentResult> ranked = new ArrayList<>(Collections.nCopies(all.size(), null));
            int rank = 0;
            double prevTotal = Double.MAX_VALUE;
            int total = 0;
            for (int idx : indices) {
                total++;
                double effectiveTotal = effectiveTotals.get(idx);
                if (effectiveTotal < prevTotal) rank = total;
                prevTotal = effectiveTotal;

                StudentResult sr = all.get(idx);
                double meanPts = sr.meanPoints();
                String grade = meanPointsToGrade(meanPts);

                ranked.set(idx, new StudentResult(
                    sr.studentId(), sr.admissionNumber(), sr.fullName(),
                    sr.form(), sr.stream(), sr.totalMarks(), (int) effectiveTotal,
                    meanPts, grade, rank, 0, total, 0
                ));
            }

            Map<String, Long> streamSizes = ranked.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(s -> s.stream(), Collectors.counting()));
            Map<String, Map<Integer, Integer>> streamRanks = computeStreamRanks(examId, ranked, effectiveTotals, indices);

            List<StudentResult> updated = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                StudentResult sr = ranked.get(i);
                Map<Integer, Integer> sRankMap = streamRanks.getOrDefault(sr.stream(), new HashMap<>());
                int effPts = effectiveTotals.get(i).intValue();
                int streamRank = sRankMap.getOrDefault(effPts, 1);
                int streamSize = streamSizes.getOrDefault(sr.stream(), 0L).intValue();
                updated.add(new StudentResult(
                    sr.studentId(), sr.admissionNumber(), sr.fullName(),
                    sr.form(), sr.stream(), sr.totalMarks(), sr.totalPoints(), sr.meanPoints(),
                    sr.meanGrade(), sr.classRank(), streamRank, sr.classSize(), streamSize
                ));
            }
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute class rankings", e);
        }
    }

    private Map<String, Map<Integer, Integer>> computeStreamRanks(long examId, List<StudentResult> all,
                                                                  List<Double> effectiveTotals, List<Integer> indices) {
        Map<String, Map<Integer, Integer>> result = new HashMap<>();
        Map<String, Integer> streamCounters = new HashMap<>();
        Map<String, Double> streamPrevPoints = new HashMap<>();

        // Sort indices by stream, then by effective total descending
        List<Integer> sortedByStream = new ArrayList<>(indices);
        sortedByStream.sort((a, b) -> {
            String sa = all.get(a).stream();
            String sb = all.get(b).stream();
            int cmp = sa.compareTo(sb);
            if (cmp != 0) return cmp;
            return Double.compare(effectiveTotals.get(b), effectiveTotals.get(a));
        });

        for (int idx : sortedByStream) {
            String stream = all.get(idx).stream();
            double pts = effectiveTotals.get(idx);
            streamCounters.put(stream, streamCounters.getOrDefault(stream, 0) + 1);
            double prev = streamPrevPoints.getOrDefault(stream, Double.MAX_VALUE);
            if (pts < prev) {
                result.computeIfAbsent(stream, k -> new HashMap<>())
                      .put((int) pts, streamCounters.get(stream));
            } else {
                result.computeIfAbsent(stream, k -> new HashMap<>())
                      .put((int) pts, result.get(stream).getOrDefault((int) prev, streamCounters.get(stream)));
            }
            streamPrevPoints.put(stream, pts);
        }
        return result;
    }

    @Override
    public Map<Long, Double> getExamStudentTotals(long examId) {
        Map<Long, Double> map = new HashMap<>();
        String sql = "SELECT student_id, ROUND(SUM(score), 1) AS total FROM marks "
            + "WHERE exam_id = ? AND (status IS NULL OR status = 'P') GROUP BY student_id";
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

    @Override
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

    @Override
    public void autoGradeExam(long examId) {
        record UngradedMark(long examId, long studentId, long subjectId, double score) {}
        List<UngradedMark> ungraded = new ArrayList<>();
        String fetchSql = """
            SELECT m.exam_id, m.student_id, m.subject_id, m.score
            FROM marks m WHERE m.exam_id = ? AND (m.grade_achieved IS NULL OR m.points_achieved IS NULL)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement fetchPs = conn.prepareStatement(fetchSql)) {
            fetchPs.setLong(1, examId);
            ResultSet rs = fetchPs.executeQuery();
            while (rs.next()) {
                ungraded.add(new UngradedMark(
                    rs.getLong("exam_id"),
                    rs.getLong("student_id"),
                    rs.getLong("subject_id"),
                    rs.getDouble("score")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch ungraded marks", e);
        }

        if (ungraded.isEmpty()) return;

        GradingSystem activeSystem = gradingSystemRepo.findActive();
        List<GradingSystemEntry> systemEntries = activeSystem != null
            ? gradingSystemRepo.findEntriesBySystem(activeSystem.getId()) : null;

        String outOfSql = "SELECT out_of FROM exam_subjects WHERE exam_id = ? AND subject_id = ?";
        String legacyGradeSql = """
            SELECT grade, points FROM grading_scales
            WHERE (subject_id IS NULL OR subject_id = ?)
              AND ? BETWEEN minimum_mark AND maximum_mark
            ORDER BY subject_id NULLS LAST, points DESC
            LIMIT 1
            """;
        String updateSql = "UPDATE marks SET grade_achieved = ?, points_achieved = ? WHERE exam_id = ? AND student_id = ? AND subject_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement outOfPs = conn.prepareStatement(outOfSql);
             PreparedStatement legacyGradePs = conn.prepareStatement(legacyGradeSql);
             PreparedStatement updatePs = conn.prepareStatement(updateSql)) {

            conn.setAutoCommit(false);
            try {
                for (UngradedMark m : ungraded) {
                    double normalizedScore = m.score;
                    outOfPs.setLong(1, m.examId);
                    outOfPs.setLong(2, m.subjectId);
                    try (ResultSet oo = outOfPs.executeQuery()) {
                        if (oo.next()) {
                            int outOf = oo.getInt("out_of");
                            if (outOf > 0 && outOf != 100) normalizedScore = (m.score / outOf) * 100;
                        }
                    }

                    String grade = null;
                    int points = 0;

                    if (systemEntries != null) {
                        // Dynamic grading system
                        for (GradingSystemEntry e : systemEntries) {
                            if (e.getSubjectId() == null || e.getSubjectId().equals(m.subjectId)) {
                                if (normalizedScore >= e.getMinimumMark() && normalizedScore <= e.getMaximumMark()) {
                                    grade = e.getGrade();
                                    points = e.getPoints();
                                    break;
                                }
                            }
                        }
                    } else {
                        // Legacy grading_scales
                        legacyGradePs.setLong(1, m.subjectId);
                        legacyGradePs.setDouble(2, normalizedScore);
                        try (ResultSet gr = legacyGradePs.executeQuery()) {
                            if (gr.next()) {
                                grade = gr.getString("grade");
                                points = gr.getInt("points");
                            }
                        }
                    }

                    updatePs.setString(1, grade);
                    updatePs.setInt(2, points);
                    updatePs.setLong(3, m.examId);
                    updatePs.setLong(4, m.studentId);
                    updatePs.setLong(5, m.subjectId);
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

    @Override
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

    @Override
    public List<ExamComparison> compareExams(long exam1Id, long exam2Id) {
        String sql = """
            SELECT s.id, s.admission_number, s.full_name, s.form, s.stream,
                   e1.total AS exam1_total, e2.total AS exam2_total
            FROM students s
            JOIN (SELECT student_id, COALESCE(SUM(points_achieved), 0) AS total FROM marks WHERE exam_id = ? GROUP BY student_id) e1 ON e1.student_id = s.id
            JOIN (SELECT student_id, COALESCE(SUM(points_achieved), 0) AS total FROM marks WHERE exam_id = ? GROUP BY student_id) e2 ON e2.student_id = s.id
            ORDER BY (e2.total - e1.total) DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, exam1Id);
            ps.setLong(2, exam2Id);
            ResultSet rs = ps.executeQuery();
            List<ExamComparison> raw = new ArrayList<>();
            while (rs.next()) {
                double e1 = rs.getDouble("exam1_total");
                double e2 = rs.getDouble("exam2_total");
                raw.add(new ExamComparison(
                    rs.getLong("id"), rs.getString("admission_number"), rs.getString("full_name"),
                    rs.getString("form"), rs.getString("stream"),
                    e1, e2, Math.round((e2 - e1) * 10.0) / 10.0,
                    0, 0, 0
                ));
            }

            Map<Long, Integer> rank1 = getExamStudentRanks(exam1Id);
            Map<Long, Integer> rank2 = getExamStudentRanks(exam2Id);

            List<ExamComparison> result = new ArrayList<>();
            for (ExamComparison ec : raw) {
                int p1 = rank1.getOrDefault(ec.studentId(), 0);
                int p2 = rank2.getOrDefault(ec.studentId(), 0);
                result.add(new ExamComparison(ec.studentId(), ec.admissionNumber(), ec.fullName(),
                    ec.form(), ec.stream(), ec.exam1Total(), ec.exam2Total(), ec.difference(),
                    p1, p2, p1 > 0 ? p1 - p2 : 0));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compare exams", e);
        }
    }

    @Override
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

    @Override
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

    @Override
    public MeritReportData computeMeritReport(long examId, String filterCol, String groupValue) {
        return computeMeritReport(examId, filterCol, groupValue, 0);
    }

    @Override
    public MeritReportData computeMeritReport(long examId, String filterCol, String groupValue, int form) {
        String validCol = DatabaseEngine.validateFilterColumn(filterCol);

        List<MeritSubject> subjects = new ArrayList<>();
        String subjSql = "SELECT DISTINCT sub.id, sub.subject_code, sub.subject_name FROM marks m JOIN subjects sub ON sub.id = m.subject_id WHERE m.exam_id = ? ORDER BY sub.subject_name";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(subjSql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                subjects.add(new MeritSubject(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
        } catch (SQLException e) { throw new RuntimeException("Failed to load subjects", e); }

        Map<Long, String[]> studentInfo = new LinkedHashMap<>();
        Map<Long, Map<Long, Double>> scores = new HashMap<>();
        Map<Long, Map<Long, Integer>> points = new HashMap<>();
        List<Long> studentOrder = new ArrayList<>();

        boolean hasForm = form > 0;
        boolean hasFilter = validCol != null && !validCol.isEmpty();
        String dataSql;
        List<Object> params = new ArrayList<>();
        params.add(examId);
        String baseSql = "SELECT s.id, s.admission_number, s.full_name, s.stream, m.subject_id, m.score, m.points_achieved FROM students s LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ?";
        List<String> whereClauses = new ArrayList<>();
        if (hasFilter) {
            whereClauses.add("s." + validCol + " = ?");
            params.add(groupValue);
        }
        if (hasForm) {
            whereClauses.add("s.form = ?");
            params.add(form);
        }
        String where = whereClauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", whereClauses);
        dataSql = baseSql + where + " ORDER BY s.id, m.subject_id";

        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(dataSql)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Long) ps.setLong(i + 1, (Long) p);
                else if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, (String) p);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long sid = rs.getLong("id");
                if (!studentInfo.containsKey(sid)) {
                    studentInfo.put(sid, new String[]{rs.getString("admission_number"), rs.getString("full_name"), rs.getString("stream")});
                    studentOrder.add(sid);
                }
                long subjId = rs.getLong("subject_id");
                if (!rs.wasNull()) {
                    scores.computeIfAbsent(sid, k -> new HashMap<>()).put(subjId, rs.getDouble("score"));
                    points.computeIfAbsent(sid, k -> new HashMap<>()).put(subjId, rs.getInt("points_achieved"));
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Failed to load student marks", e); }

        Map<Long, Double> means = new HashMap<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT subject_id, AVG(score) AS m FROM marks WHERE exam_id = ? GROUP BY subject_id")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) means.put(rs.getLong("subject_id"), rs.getDouble("m"));
        } catch (SQLException e) { throw new RuntimeException("Failed to compute subject means", e); }

        Map<Long, Map<Long, Integer>> subjectPositions = new HashMap<>();
        Map<Long, List<Map.Entry<Long, Double>>> subjScoreList = new HashMap<>();
        for (var se : scores.entrySet()) {
            long sid = se.getKey();
            for (var sse : se.getValue().entrySet())
                subjScoreList.computeIfAbsent(sse.getKey(), k -> new ArrayList<>()).add(Map.entry(sid, sse.getValue()));
        }
        for (var entry : subjScoreList.entrySet()) {
            long subjId = entry.getKey();
            var list = entry.getValue();
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int r = 1;
            double prev = Double.MAX_VALUE;
            for (int i = 0; i < list.size(); i++) {
                var e = list.get(i);
                if (e.getValue() < prev) r = i + 1;
                prev = e.getValue();
                subjectPositions.computeIfAbsent(subjId, k -> new HashMap<>()).put(e.getKey(), r);
            }
        }

        record Srd(long id, String adm, String name, String stream, double totalMarks, double effectiveTotal) {}
        RankingProfile activeProfile = getActiveRankingProfile();
        List<Srd> ranked = new ArrayList<>();
        for (long sid : studentOrder) {
            var sMap = scores.getOrDefault(sid, Collections.emptyMap());
            double tMarks = sMap.values().stream().mapToDouble(v -> v).sum();
            double effectiveTotal;
            if (activeProfile != null) {
                effectiveTotal = computeWeightedTotal(sid, examId, activeProfile);
            } else {
                var pMap = points.getOrDefault(sid, Collections.emptyMap());
                effectiveTotal = pMap.values().stream().mapToInt(v -> v).sum();
            }
            String[] info = studentInfo.get(sid);
            ranked.add(new Srd(sid, info[0], info[1], info[2], tMarks, effectiveTotal));
        }
        ranked.sort((a, b) -> Double.compare(b.effectiveTotal, a.effectiveTotal));

        List<MeritStudent> resultStudents = new ArrayList<>();
        int rank = 0;
        double prevPts = Double.MAX_VALUE;
        for (int i = 0; i < ranked.size(); i++) {
            Srd rd = ranked.get(i);
            if (rd.effectiveTotal < prevPts) rank = i + 1;
            prevPts = rd.effectiveTotal;

            var studentScores = scores.getOrDefault(rd.id, Collections.emptyMap());
            var studentPts = points.getOrDefault(rd.id, Collections.emptyMap());
            int subjCount = studentPts.size();
            double meanPts = subjCount > 0 ? Math.round(rd.effectiveTotal / subjCount * 10.0) / 10.0 : 0;
            String grade = meanPointsToGrade(meanPts);

            Map<Long, Double> deviations = new HashMap<>();
            for (var se : studentScores.entrySet()) {
                double mean = means.getOrDefault(se.getKey(), 0.0);
                deviations.put(se.getKey(), Math.round((se.getValue() - mean) * 10.0) / 10.0);
            }

            Map<Long, Integer> studentSubjectPositions = new HashMap<>();
            for (long subjId : subjectPositions.keySet()) {
                Integer pos = subjectPositions.get(subjId).get(rd.id);
                if (pos != null) studentSubjectPositions.put(subjId, pos);
            }

            resultStudents.add(new MeritStudent(rd.id, rd.adm, rd.name, rd.stream,
                rd.totalMarks, (int) Math.round(rd.effectiveTotal), meanPts, grade, rank,
                studentScores, deviations, studentSubjectPositions));
        }

        return new MeritReportData(subjects, resultStudents);
    }

    @Override
    public String determineGradeAndPoints(double score, Long subjectId, Long examId) {
        double normalizedScore = normalizeByOutOf(score, subjectId, examId);

        // Try dynamic grading system first
        String dynamicResult = resolveGradeAndPoints(normalizedScore, subjectId);
        if (dynamicResult != null) return dynamicResult;

        // Fall back to legacy grading_scales
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
            ps.setDouble(2, normalizedScore);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("grade") + "|" + rs.getInt("points");
            }
            return "E|0";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to determine grade", e);
        }
    }

    @Override
    public List<MeritSubject> getSubjectsForExam(long examId) {
        List<MeritSubject> list = new ArrayList<>();
        String sql = "SELECT DISTINCT sub.id, sub.subject_code, sub.subject_name FROM marks m "
            + "JOIN subjects sub ON sub.id = m.subject_id WHERE m.exam_id = ? AND (m.status IS NULL OR m.status = 'P') ORDER BY sub.subject_name";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(new MeritSubject(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
        } catch (SQLException e) { LoggerUtil.warn("Failed to load subjects for exam " + examId + ": " + e.getMessage()); }
        return list;
    }

    @Override
    public int computeBestOfNPoints(long studentId, long examId, int n) {
        String sql = "SELECT points_achieved FROM marks WHERE student_id = ? AND exam_id = ? "
            + "AND (status IS NULL OR status = 'P') AND points_achieved IS NOT NULL "
            + "ORDER BY points_achieved DESC LIMIT ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, examId);
            ps.setInt(3, n);
            ResultSet rs = ps.executeQuery();
            int total = 0;
            while (rs.next()) total += rs.getInt(1);
            return total;
        } catch (SQLException e) { return 0; }
    }

    @Override
    public String meanPointsToGrade(double meanPoints) {
        // Try dynamic grading system first
        String dynamicResult = resolveMeanPointsToGrade(meanPoints);
        if (dynamicResult != null) return dynamicResult;

        // Fall back to legacy grading_scales
        String sql = "SELECT grade FROM grading_scales WHERE subject_id IS NULL AND points <= ? ORDER BY points DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, meanPoints);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("grade");
            return "E";
        } catch (SQLException e) {
            return "E";
        }
    }

    @Override
    public String computeMeanGradeFromPoints(double meanPoints) {
        return meanPointsToGrade(meanPoints);
    }

    @Override
    public double computeDeviation(long studentId, long examId, long subjectId) {
        double studentScore = 0;
        double classAvg = 0;
        String scoreSql = "SELECT score FROM marks WHERE exam_id = ? AND student_id = ? AND subject_id = ?";
        String avgSql = "SELECT AVG(score) FROM marks WHERE exam_id = ? AND subject_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(scoreSql);
             PreparedStatement ps2 = conn.prepareStatement(avgSql)) {
            ps1.setLong(1, examId); ps1.setLong(2, studentId); ps1.setLong(3, subjectId);
            ResultSet rs = ps1.executeQuery();
            if (rs.next()) studentScore = rs.getDouble("score");
            ps2.setLong(1, examId); ps2.setLong(2, subjectId);
            rs = ps2.executeQuery();
            if (rs.next()) classAvg = rs.getDouble(1);
        } catch (SQLException e) { LoggerUtil.warn("Failed to compute deviation for student " + studentId + ": " + e.getMessage()); }
        return classAvg > 0 ? Math.round((studentScore - classAvg) * 10.0) / 10.0 : 0;
    }

    @Override
    public double normalizeByOutOf(double score, Long subjectId, Long examId) {
        if (examId == null || subjectId == null) return score;
        String sql = "SELECT out_of FROM exam_subjects WHERE exam_id = ? AND subject_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int outOf = rs.getInt("out_of");
                if (outOf > 0 && outOf != 100) return (score / outOf) * 100;
            }
            return score;
        } catch (SQLException e) {
            return score;
        }
    }

    @Override
    public ExamSummary computeExamSummary(long examId) {
        GradingSystem activeSystem = gradingSystemRepo.findActive();

        String overviewSql = """
            SELECT
                COUNT(DISTINCT m.student_id) AS total_students,
                COUNT(DISTINCT m.subject_id) AS total_subjects,
                ROUND(AVG(m.score), 1) AS overall_mean,
                ROUND(MAX(m.score), 1) AS highest_score,
                ROUND(MIN(m.score), 1) AS lowest_score
            FROM marks m WHERE m.exam_id = ?
            """;
        String bestWorstSql = """
            SELECT sub.subject_name, ROUND(AVG(m.score), 1) AS mean_score
            FROM marks m JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ?
            GROUP BY sub.id, sub.subject_name
            ORDER BY mean_score DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(overviewSql);
             PreparedStatement ps3 = conn.prepareStatement(bestWorstSql)) {

            ps1.setLong(1, examId);
            ResultSet rs1 = ps1.executeQuery();
            int totalStudents = 0, totalSubjects = 0;
            double mean = 0, highest = 0, lowest = 0;
            if (rs1.next()) {
                totalStudents = rs1.getInt("total_students");
                totalSubjects = rs1.getInt("total_subjects");
                mean = rs1.getDouble("overall_mean");
                highest = rs1.getDouble("highest_score");
                lowest = rs1.getDouble("lowest_score");
            }

            // Compute pass count using dynamic grading system
            int passCount = 0;
            String passSql;
            if (activeSystem != null) {
                passSql = "SELECT COUNT(DISTINCT student_id) AS pass_count FROM marks WHERE exam_id = ? AND grade_achieved IS NOT NULL AND points_achieved >= 6";
            } else {
                passSql = """
                    SELECT COUNT(DISTINCT m.student_id) AS pass_count
                    FROM marks m
                    JOIN grading_scales g ON (g.subject_id IS NULL OR g.subject_id = m.subject_id)
                        AND m.score BETWEEN g.minimum_mark AND g.maximum_mark
                    WHERE m.exam_id = ? AND g.points >= 6
                    """;
            }
            try (PreparedStatement ps2 = conn.prepareStatement(passSql)) {
                ps2.setLong(1, examId);
                ResultSet rs2 = ps2.executeQuery();
                passCount = rs2.next() ? rs2.getInt("pass_count") : 0;
            }

            ps3.setLong(1, examId);
            ResultSet rs3 = ps3.executeQuery();
            String bestSubject = "", worstSubject = "";
            while (rs3.next()) {
                String n = rs3.getString("subject_name");
                if (bestSubject.isEmpty()) bestSubject = n;
                worstSubject = n;
            }

            return new ExamSummary(totalStudents, totalSubjects, mean, highest, lowest,
                passCount, totalStudents, bestSubject, worstSubject);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute exam summary", e);
        }
    }

    @Override
    public List<WeakArea> computeWeakAreas(long examId) {
        String sql = """
            SELECT sub.subject_name, ROUND(AVG(m.score), 1) AS mean_score
            FROM marks m JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ?
            GROUP BY sub.id, sub.subject_name
            ORDER BY mean_score ASC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            List<String> subjectNames = new ArrayList<>();
            List<Double> meanScores = new ArrayList<>();
            while (rs.next()) {
                subjectNames.add(rs.getString("subject_name"));
                meanScores.add(rs.getDouble("mean_score"));
            }
            rs.close();
            ps.close();
            List<WeakArea> list = new ArrayList<>();
            for (int i = 0; i < subjectNames.size(); i++) {
                String grade = determineGradeAndPoints(meanScores.get(i), null, examId).split("\\|")[0];
                list.add(new WeakArea(subjectNames.get(i), meanScores.get(i), grade));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute weak areas", e);
        }
    }

    @Override
    public List<ClassTrend> computeClassTrends() {
        String sql = """
            SELECT e.id, e.academic_year || ' ' || e.term || ' ' || e.exam_series AS label,
                   ROUND(AVG(m.score), 1) AS mean_score
            FROM marks m JOIN exams e ON e.id = m.exam_id
            GROUP BY e.id, e.academic_year, e.term, e.exam_series
            ORDER BY e.id
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ClassTrend> list = new ArrayList<>();
            while (rs.next())
                list.add(new ClassTrend(rs.getLong("id"), rs.getString("label"), rs.getDouble("mean_score")));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute class trends", e);
        }
    }

    @Override
    public List<StudentWeakArea> computeStudentWeakAreas(long examId, long studentId) {
        String sql = """
            SELECT sub.subject_name, m.score, m.grade_achieved,
                   ROUND((SELECT AVG(m2.score) FROM marks m2 WHERE m2.exam_id = ? AND m2.subject_id = m.subject_id), 1) AS class_mean
            FROM marks m JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ? AND m.student_id = ?
            ORDER BY m.score ASC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, examId);
            ps.setLong(3, studentId);
            ResultSet rs = ps.executeQuery();
            List<StudentWeakArea> list = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("subject_name");
                double score = rs.getDouble("score");
                String grade = rs.getString("grade_achieved");
                double classMean = rs.getDouble("class_mean");
                double deviation = Math.round((score - classMean) * 10.0) / 10.0;
                list.add(new StudentWeakArea(name, score, grade != null ? grade : "N/A", classMean, deviation));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute student weak areas", e);
        }
    }
}
