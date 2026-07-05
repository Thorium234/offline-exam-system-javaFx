package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExamRepositoryTest extends DatabaseTestBase {

    private final IExamRepository repo = new ExamRepositoryImpl();

    @Test
    void insertAndFindById() {
        repo.insert("2026", "Term 1", "End Term");
        Map<String, Object> found = repo.findById(1L);
        assertNotNull(found);
        assertEquals("2026", found.get("academic_year"));
        assertEquals("Term 1", found.get("term"));
        assertEquals("End Term", found.get("exam_series"));
    }

    @Test
    void findAll_returnsAllExams() {
        repo.insert("2026", "Term 1", "End Term");
        repo.insert("2026", "Term 2", "Mid Term");
        assertEquals(2, repo.findAll().size());
    }

    @Test
    void findAllDesc_ordersByIdDescending() {
        repo.insert("2026", "Term 1", "End Term");
        repo.insert("2026", "Term 2", "Mid Term");
        var exams = repo.findAllDesc();
        assertEquals(2, exams.size());
        assertTrue((Long) exams.get(0).get("id") > (Long) exams.get(1).get("id"));
    }

    @Test
    void update() {
        repo.insert("2026", "Term 1", "End Term");
        repo.update(1L, "2026", "Term 2", "Mid Term");
        Map<String, Object> found = repo.findById(1L);
        assertEquals("Term 2", found.get("term"));
        assertEquals("Mid Term", found.get("exam_series"));
    }

    @Test
    void delete_removesExamAndRelatedData() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        repo.insert("2026", "Term 1", "End Term");
        insertExamSubject(1L, subj, 100);
        repo.delete(1L);
        assertNull(repo.findById(1L));
    }

    @Test
    void release_workflow() {
        repo.insert("2026", "Term 1", "End Term");
        assertFalse(repo.isReleased(1L));
        repo.release(1L, "admin");
        assertTrue(repo.isReleased(1L));
    }

    @Test
    void getPreviousExamId() {
        repo.insert("2026", "Term 1", "End Term");
        repo.insert("2026", "Term 2", "Mid Term");
        assertEquals(1L, repo.getPreviousExamId(2L));
        assertEquals(-1L, repo.getPreviousExamId(1L));
    }

    @Test
    void getMaxMarks() {
        repo.insert("2026", "Term 1", "End Term");
        assertEquals(0, repo.getMaxMarks(1L));
        insertExamSubject(1L, insertSubject("MATH", "Mathematics", "Math", "Compulsory"), 100);
        insertExamSubject(1L, insertSubject("ENG", "English", "Languages", "Compulsory"), 50);
        assertEquals(150, repo.getMaxMarks(1L));
    }

    @Test
    void getFirstExamId() {
        assertEquals(-1, repo.getFirstExamId());
        repo.insert("2026", "Term 1", "End Term");
        repo.insert("2026", "Term 2", "Mid Term");
        assertEquals(1L, repo.getFirstExamId());
    }

    @Test
    void findLatestIds() {
        for (int i = 1; i <= 5; i++) {
            repo.insert("2026", "Term 1", "Series " + i);
        }
        var latest = repo.findLatestIds(3);
        assertEquals(3, latest.size());
        assertTrue(latest.get(0) > latest.get(1));
    }

    @Test
    void findById_returnsNullForMissing() {
        assertNull(repo.findById(999L));
    }

    @Test
    void isReleased_returnsFalseForMissing() {
        assertFalse(repo.isReleased(999L));
    }

    @Test
    void getPreviousExamId_returnsMinusOneForMissing() {
        assertEquals(-1, repo.getPreviousExamId(999L));
    }
}
