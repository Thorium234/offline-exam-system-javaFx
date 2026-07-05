package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.config.CurriculumSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GradingScaleRepositoryTest extends DatabaseTestBase {

    private final IGradingScaleRepository repo = new GradingScaleRepositoryImpl();

    @Test
    void insertAndFindAllWithSubject() {
        repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.insert(null, 60, 79, "B", 10, "Good");
        var all = repo.findAllWithSubject();
        assertEquals(2, all.size());
        assertEquals("A", all.get(0).get("grade"));
        assertEquals("B", all.get(1).get("grade"));
    }

    @Test
    void findAllWithSubject_showsSubjectNameForPerSubjectScale() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        repo.insert(subj, 80, 100, "A", 12, "Excellent");
        repo.insert(null, 60, 79, "B", 10, "Good");
        var all = repo.findAllWithSubject();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(r -> r.get("subject_name").equals("Mathematics")));
        assertTrue(all.stream().anyMatch(r -> r.get("subject_name").equals("** Global **")));
    }

    @Test
    void update() {
        long id = repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.update(id, null, 85, 100, "A+", 13, "Outstanding");
        var all = repo.findAllWithSubject();
        assertEquals(1, all.size());
        assertEquals("A+", all.get(0).get("grade"));
    }

    @Test
    void deleteById() {
        long id = repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.deleteById(id);
        assertTrue(repo.findAllWithSubject().isEmpty());
    }

    @Test
    void deleteGlobal() {
        repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.insert(insertSubject("MATH", "Math", "Math", "Compulsory"), 80, 100, "A", 12, "Excellent");
        repo.deleteGlobal();
        var all = repo.findAllWithSubject();
        assertEquals(1, all.size());
        assertNotNull(all.get(0).get("subject_id"));
    }

    @Test
    void batchInsertGlobal() {
        repo.insertBatchGlobal(CurriculumSystem.SYSTEM_844);
        var all = repo.findAllWithSubject();
        assertFalse(all.isEmpty());
        assertEquals(12, all.size());
    }

    @Test
    void findAllSubjectsForCombo() {
        insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        insertSubject("ENG", "English", "Languages", "Compulsory");
        var subjects = repo.findAllSubjectsForCombo();
        assertEquals(2, subjects.size());
    }

    @Test
    void insertPerSubjectScale() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        repo.insert(subj, 90, 100, "A*", 12, "Excellent");
        var all = repo.findAllWithSubject();
        assertEquals(1, all.size());
        assertEquals("A*", all.get(0).get("grade"));
    }

    @Test
    void update_withNullSubjectId() {
        long id = repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.update(id, null, 0, 100, "A", 12, "Changed");
        var all = repo.findAllWithSubject();
        assertEquals("Changed", all.get(0).get("remarks"));
    }
}
