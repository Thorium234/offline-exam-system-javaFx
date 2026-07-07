package com.zaraki.exams.service;

import java.util.List;
import java.util.Map;

/**
 * Service for exam analysis operations: grading, ranking, merit, trends, comparisons.
 */
public interface IExamAnalysisService {

    /** Metrics for a single subject across an exam. */
    public record SubjectMetrics(long subjectId, String subjectName, String department,
                                 double meanScore, String meanGrade,
                                 double stdDev, int subjectRank, int totalCandidates) {}

    /** Aggregated exam result for a single student. */
    public record StudentResult(long studentId, String admissionNumber, String fullName,
                                String form, String stream,
                                double totalMarks, int totalPoints, double meanPoints,
                                String meanGrade, int classRank, int streamRank,
                                int classSize, int streamSize) {}

    /** Subject entry in a merit report. */
    public record MeritSubject(long id, String code, String name) {}
    /** Student entry in a merit report with per-subject scores and deviations. */
    public record MeritStudent(long studentId, String admissionNumber, String fullName, String stream,
                               double totalMarks, int totalPoints, double meanPoints, String meanGrade,
                               int rank, Map<Long, Double> scores, Map<Long, Double> deviations,
                               Map<Long, Integer> subjectPositions) {}
    /** Full merit report: subjects + ranked students. */
    public record MeritReportData(List<MeritSubject> subjects, List<MeritStudent> students) {}

    /** Grade distribution for a single subject. */
    public record GradeDistribution(String subjectName, Map<String, Integer> gradeCounts) {}
    /** Comparison of a student's performance between two exams. */
    public record ExamComparison(long studentId, String admissionNumber, String fullName,
                                  String form, String stream,
                                  double exam1Total, double exam2Total, double difference,
                                  int exam1Pos, int exam2Pos, int posChange) {}
    /** A single data point in a student's trend across exams. */
    public record StudentTrend(long examId, String examLabel, double totalPoints) {}

    /** Summary statistics for an entire exam. */
    public record ExamSummary(int totalStudents, int totalSubjects,
                              double overallMean, double highestScore, double lowestScore,
                              int passCount, int totalCount,
                              String bestSubject, String worstSubject) {
        /** Returns pass rate as a percentage (0-100). */
        public double passRate() { return totalCount > 0 ? Math.round((double) passCount / totalCount * 1000.0) / 10.0 : 0; }
    }

    /** Weak area identified for a subject at exam level. */
    public record WeakArea(String subjectName, double meanScore, String grade) {}
    /** Mean score trend across exams. */
    public record ClassTrend(long examId, String examLabel, double meanScore) {}
    /** Weak area identified for an individual student. */
    public record StudentWeakArea(String subjectName, double score, String grade, double classMean, double deviation) {}

    /** Computes per-subject metrics (mean, std-dev, rank, grade) for an exam. */
    List<SubjectMetrics> computeSubjectMetrics(long examId);
    /** Computes ranked student results for an exam. */
    List<StudentResult> computeClassRankings(long examId);
    /** Returns total marks per student for an exam. */
    Map<Long, Double> getExamStudentTotals(long examId);
    /** Returns rank per student for an exam. */
    Map<Long, Integer> getExamStudentRanks(long examId);
    /** Auto-grades all marks for an exam (assigns grades and points). */
    void autoGradeExam(long examId);
    /** Computes grade distribution per subject for an exam. */
    List<GradeDistribution> computeGradeDistribution(long examId);
    /** Compares student performance between two exams. */
    List<ExamComparison> compareExams(long exam1Id, long exam2Id);
    /** Finds the exam ID immediately before the given one. */
    long findPreviousExam(long examId);
    /** Computes a student's performance trend across all exams. */
    List<StudentTrend> computeStudentTrend(long studentId);
    /** Generates a full merit report filtered by column and group value. */
    MeritReportData computeMeritReport(long examId, String filterCol, String groupValue);
    /** Generates a merit report for a specific form. */
    MeritReportData computeMeritReport(long examId, String filterCol, String groupValue, int form);
    /** Determines the grade and points for a given score, normalized by out_of. Returns "GRADE|POINTS". */
    String determineGradeAndPoints(double score, Long subjectId, Long examId);
    /** Returns the subjects associated with an exam. */
    List<MeritSubject> getSubjectsForExam(long examId);
    /** Computes Best-of-N points for a student in an exam. */
    int computeBestOfNPoints(long studentId, long examId, int n);
    /** Converts mean points to a grade string. */
    String meanPointsToGrade(double meanPoints);
    /** Converts mean points to a grade string (alias). */
    String computeMeanGradeFromPoints(double meanPoints);
    /** Computes deviation of a student's score from the class mean for a subject. */
    double computeDeviation(long studentId, long examId, long subjectId);
    /** Normalizes a score by the subject's out_of factor. */
    double normalizeByOutOf(double score, Long subjectId, Long examId);
    /** Computes exam-level summary statistics. */
    ExamSummary computeExamSummary(long examId);
    /** Identifies weak subjects at exam level (lowest mean scores). */
    List<WeakArea> computeWeakAreas(long examId);
    /** Computes mean score trend across all exams. */
    List<ClassTrend> computeClassTrends();
    /** Identifies weak subjects for a specific student. */
    List<StudentWeakArea> computeStudentWeakAreas(long examId, long studentId);
}
