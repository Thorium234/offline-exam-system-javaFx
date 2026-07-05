package com.zaraki.exams.service;

import java.util.List;
import java.util.Map;

public interface IExamAnalysisService {

    public record SubjectMetrics(long subjectId, String subjectName, String department,
                                 double meanScore, String meanGrade,
                                 double stdDev, int subjectRank, int totalCandidates) {}

    public record StudentResult(long studentId, String admissionNumber, String fullName,
                                String form, String stream,
                                double totalMarks, int totalPoints, double meanPoints,
                                String meanGrade, int classRank, int streamRank,
                                int classSize, int streamSize) {}

    public record MeritSubject(long id, String code, String name) {}
    public record MeritStudent(long studentId, String admissionNumber, String fullName, String stream,
                               double totalMarks, int totalPoints, double meanPoints, String meanGrade,
                               int rank, Map<Long, Double> scores, Map<Long, Double> deviations,
                               Map<Long, Integer> subjectPositions) {}

    public record MeritReportData(List<MeritSubject> subjects, List<MeritStudent> students) {}

    public record GradeDistribution(String subjectName, Map<String, Integer> gradeCounts) {}
    public record ExamComparison(long studentId, String admissionNumber, String fullName,
                                  String form, String stream,
                                  double exam1Total, double exam2Total, double difference,
                                  int exam1Pos, int exam2Pos, int posChange) {}
    public record StudentTrend(long examId, String examLabel, double totalPoints) {}

    public record ExamSummary(int totalStudents, int totalSubjects,
                              double overallMean, double highestScore, double lowestScore,
                              int passCount, int totalCount,
                              String bestSubject, String worstSubject) {
        public double passRate() { return totalCount > 0 ? Math.round((double) passCount / totalCount * 1000.0) / 10.0 : 0; }
    }

    public record WeakArea(String subjectName, double meanScore, String grade) {}
    public record ClassTrend(long examId, String examLabel, double meanScore) {}
    public record StudentWeakArea(String subjectName, double score, String grade, double classMean, double deviation) {}

    List<SubjectMetrics> computeSubjectMetrics(long examId);
    List<StudentResult> computeClassRankings(long examId);
    Map<Long, Double> getExamStudentTotals(long examId);
    Map<Long, Integer> getExamStudentRanks(long examId);
    void autoGradeExam(long examId);
    List<GradeDistribution> computeGradeDistribution(long examId);
    List<ExamComparison> compareExams(long exam1Id, long exam2Id);
    long findPreviousExam(long examId);
    List<StudentTrend> computeStudentTrend(long studentId);
    MeritReportData computeMeritReport(long examId, String filterCol, String groupValue);
    MeritReportData computeMeritReport(long examId, String filterCol, String groupValue, int form);
    String determineGradeAndPoints(double score, Long subjectId, Long examId);
    List<MeritSubject> getSubjectsForExam(long examId);
    int computeBestOfNPoints(long studentId, long examId, int n);
    String meanPointsToGrade(double meanPoints);
    String computeMeanGradeFromPoints(double meanPoints);
    double computeDeviation(long studentId, long examId, long subjectId);
    double normalizeByOutOf(double score, Long subjectId, Long examId);
    ExamSummary computeExamSummary(long examId);
    List<WeakArea> computeWeakAreas(long examId);
    List<ClassTrend> computeClassTrends();
    List<StudentWeakArea> computeStudentWeakAreas(long examId, long studentId);
}
