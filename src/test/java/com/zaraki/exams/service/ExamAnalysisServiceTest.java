package com.zaraki.exams.service;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExamAnalysisServiceTest extends DatabaseTestBase {

    private final         IExamAnalysisService service = new ExamAnalysisServiceImpl();

    @Test
    void normalizeByOutOf_returnsOriginalScoreWhenOutOf100() {
        double result = service.normalizeByOutOf(85.0, 1L, 1L);
        assertEquals(85.0, result);
    }

    @Test
    void normalizeByOutOf_scalesScoreWhenOutOfIsNot100() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 50);
        double result = service.normalizeByOutOf(40.0, subj, exam);
        assertEquals(80.0, result);
    }

    @Test
    void normalizeByOutOf_returnsOriginalForNullIds() {
        assertEquals(85.0, service.normalizeByOutOf(85.0, null, null));
        assertEquals(85.0, service.normalizeByOutOf(85.0, 1L, null));
    }

    @Test
    void computeSubjectMetrics_returnsEmptyForInvalidExam() {
        assertTrue(service.computeSubjectMetrics(-999L).isEmpty());
    }

    @Test
    void computeSubjectMetrics_withData() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        var metrics = service.computeSubjectMetrics(exam);
        assertEquals(1, metrics.size());
        assertEquals(80.0, metrics.get(0).meanScore());
    }

    @Test
    void computeClassRankings_returnsEmptyForInvalidExam() {
        assertTrue(service.computeClassRankings(-999L).isEmpty());
    }

    @Test
    void computeClassRankings_withData() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 60, 79, "B", 10, "Good");

        var rankings = service.computeClassRankings(exam);
        assertEquals(2, rankings.size());
        assertEquals(1, rankings.get(0).classRank());
        assertEquals(2, rankings.get(1).classRank());
    }

    @Test
    void denseRanking_withTies() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        long s3 = insertStudent("1003", "Charlie", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 90, "A", 12);
        insertMark(exam, s3, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 60, 79, "B", 10, "Good");

        var rankings = service.computeClassRankings(exam);
        assertEquals(3, rankings.size());
        assertEquals(rankings.get(0).classRank(), rankings.get(1).classRank());
        assertTrue(rankings.get(2).classRank() > rankings.get(0).classRank());
    }

    @Test
    void computeSubjectMetrics_withStdDev() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        long s3 = insertStudent("1003", "Charlie", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 100, "A", 12);
        insertMark(exam, s2, subj, 50, "C", 6);
        insertMark(exam, s3, subj, 60, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 60, 79, "B", 10, "Good");
        insertGradeScale(null, 0, 59, "C", 6, "Average");

        var metrics = service.computeSubjectMetrics(exam);
        assertEquals(1, metrics.size());
        assertTrue(metrics.get(0).stdDev() > 0);
    }

    @Test
    void gradeDistribution() {
        long math = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long eng = insertSubject("ENG", "English", "Languages", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertMark(exam, s1, math, 90, "A", 12);
        insertMark(exam, s1, eng, 70, "B", 10);
        insertMark(exam, s2, math, 80, "A", 12);

        var dist = service.computeGradeDistribution(exam);
        assertEquals(2, dist.size());
    }

    @Test
    void gradeDistribution_noMarks() {
        assertTrue(service.computeGradeDistribution(-999L).isEmpty());
    }

    @Test
    void examComparison() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam1 = insertExam("2026", "Term 1", "End Term");
        long exam2 = insertExam("2026", "Term 2", "Mid Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam1, subj, 100);
        insertExamSubject(exam2, subj, 100);
        insertMark(exam1, s1, subj, 90, "A", 12);
        insertMark(exam2, s1, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        var comparison = service.compareExams(exam1, exam2);
        assertEquals(1, comparison.size());
        assertEquals("Alice", comparison.get(0).fullName());
    }

    @Test
    void findPreviousExam() {
        long exam1 = insertExam("2026", "Term 1", "End Term");
        long exam2 = insertExam("2026", "Term 2", "Mid Term");
        assertEquals(exam1, service.findPreviousExam(exam2));
        assertEquals(-1, service.findPreviousExam(exam1));
    }

    @Test
    void getExamStudentTotals() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90);
        insertMark(exam, s2, subj, 70);

        var totals = service.getExamStudentTotals(exam);
        assertEquals(2, totals.size());
        assertEquals(90.0, totals.get(s1));
    }

    @Test
    void getExamStudentRanks() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90);
        insertMark(exam, s2, subj, 70);

        var ranks = service.getExamStudentRanks(exam);
        assertEquals(1, ranks.get(s1));
        assertEquals(2, ranks.get(s2));
    }

    @Test
    void meritReport() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        var report = service.computeMeritReport(exam, "stream", "East");
        assertEquals(1, report.subjects().size());
        assertEquals(2, report.students().size());
    }

    @Test
    void meritReport_withFormFilter() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 2, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        var report = service.computeMeritReport(exam, "stream", "East", 1);
        assertEquals(1, report.students().size());
        assertEquals("Alice", report.students().get(0).fullName());
    }

    @Test
    void bestOfN() {
        long subj1 = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long subj2 = insertSubject("ENG", "English", "Languages", "Compulsory");
        long subj3 = insertSubject("SCI", "Science", "Science", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj1, 100);
        insertExamSubject(exam, subj2, 100);
        insertExamSubject(exam, subj3, 100);
        insertMark(exam, student, subj1, 90, "A", 12);
        insertMark(exam, student, subj2, 80, "A", 12);
        insertMark(exam, student, subj3, 70, "B", 10);

        int best2 = service.computeBestOfNPoints(student, exam, 2);
        assertEquals(24, best2);
    }

    @Test
    void examSummary() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90, "A", 12);
        insertMark(exam, s2, subj, 70, "B", 10);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 60, 79, "B", 10, "Good");
        insertGradeScale(null, 0, 59, "C", 6, "Average");

        var summary = service.computeExamSummary(exam);
        assertEquals(2, summary.totalStudents());
        assertEquals(1, summary.totalSubjects());
        assertTrue(summary.overallMean() > 0);
    }

    @Test
    void weakAreas() {
        long subj1 = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long subj2 = insertSubject("ENG", "English", "Languages", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj1, 100);
        insertExamSubject(exam, subj2, 100);
        insertMark(exam, s1, subj1, 90, "A", 12);
        insertMark(exam, s1, subj2, 30, "E", 1);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "E", 1, "Fail");

        var areas = service.computeWeakAreas(exam);
        assertEquals(2, areas.size());
    }

    @Test
    void computerStudentTrend() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam1 = insertExam("2026", "Term 1", "End Term");
        long exam2 = insertExam("2026", "Term 2", "Mid Term");
        long student = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam1, subj, 100);
        insertExamSubject(exam2, subj, 100);
        insertMark(exam1, student, subj, 90, "A", 12);
        insertMark(exam2, student, subj, 70, "B", 10);

        var trend = service.computeStudentTrend(student);
        assertEquals(2, trend.size());
    }

    @Test
    void computeDeviation() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90);
        insertMark(exam, s2, subj, 70);

        double dev = service.computeDeviation(s1, exam, subj);
        assertTrue(dev > 0);
    }

    @Test
    void autoGrade() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");
        insertMark(exam, student, subj, 85);

        service.autoGradeExam(exam);
        var mark = service.computeSubjectMetrics(exam);
        assertFalse(mark.isEmpty());
    }

    @Test
    void meanPointsToGrade() {
        insertGradeScale(null, 0, 10, "A", 20, "Custom A");
        insertGradeScale(null, 0, 10, "B", 15, "Custom B");
        assertEquals("A", service.meanPointsToGrade(20));
        assertEquals("B", service.meanPointsToGrade(15));
        assertEquals("E", service.meanPointsToGrade(0));
    }

    @Test
    void computeClassTrends() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 85);

        var trends = service.computeClassTrends();
        assertEquals(1, trends.size());
    }

    @Test
    void computeStudentWeakAreas() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 50, "C", 6);

        var areas = service.computeStudentWeakAreas(exam, s1);
        assertEquals(1, areas.size());
        assertEquals("Mathematics", areas.get(0).subjectName());
    }

    @Test
    void emptyExamReturnsEmptyLists() {
        assertTrue(service.computeClassRankings(-1L).isEmpty());
        assertTrue(service.computeSubjectMetrics(-1L).isEmpty());
        assertTrue(service.computeGradeDistribution(-1L).isEmpty());
        assertTrue(service.compareExams(-1L, -2L).isEmpty());
        assertTrue(service.getExamStudentTotals(-1L).isEmpty());
        assertTrue(service.computeWeakAreas(-1L).isEmpty());
        assertTrue(service.computeStudentWeakAreas(-1L, -1L).isEmpty());
        assertTrue(service.computeStudentTrend(-1L).isEmpty());
    }

    @Test
    void singleStudent_allSameScores() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 75, "B", 10);
        insertGradeScale(null, 0, 100, "B", 10, "Good");

        var metrics = service.computeSubjectMetrics(exam);
        assertEquals(1, metrics.size());
        assertEquals(1, service.computeClassRankings(exam).size());
    }

    @Test
    void determineGradeAndPoints_withNormalization() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 50);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        // 40 out of 50 -> 80 out of 100 -> A
        String gp = service.determineGradeAndPoints(40.0, subj, exam);
        assertEquals("A|12", gp);
    }

    @Test
    void getSubjectsForExam() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        insertExamSubject(exam, subj, 100);
        insertMark(exam, s1, subj, 90);
        var subjects = service.getSubjectsForExam(exam);
        assertEquals(1, subjects.size());
    }

    @Test
    void computeMeanGradeFromPoints() {
        // Default grading scale has A-=11, so 11 points maps to A-
        assertEquals("A-", service.computeMeanGradeFromPoints(11));
    }
}
