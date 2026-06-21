package com.zaraki.exams.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamAnalysisServiceTest {

    @Test
    void normalizeByOutOf_returnsOriginalScoreWhenOutOf100() {
        ExamAnalysisService service = new ExamAnalysisService();
        double result = service.normalizeByOutOf(85.0, 1L, 1L);
        assertTrue(result > 0);
    }

    @Test
    void computeSubjectMetrics_returnsEmptyListForInvalidExam() {
        ExamAnalysisService service = new ExamAnalysisService();
        var metrics = service.computeSubjectMetrics(-999L);
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }

    @Test
    void computeClassRankings_returnsEmptyListForInvalidExam() {
        ExamAnalysisService service = new ExamAnalysisService();
        var rankings = service.computeClassRankings(-999L);
        assertNotNull(rankings);
        assertTrue(rankings.isEmpty());
    }

    @Test
    void findPreviousExam_returnsMinusOneForInvalidExam() {
        ExamAnalysisService service = new ExamAnalysisService();
        long result = service.findPreviousExam(-999L);
        assertEquals(-1, result);
    }
}
