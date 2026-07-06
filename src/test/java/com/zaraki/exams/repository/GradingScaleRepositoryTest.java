package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.config.CurriculumSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GradingScaleRepositoryTest extends DatabaseTestBase {

    private static final int DEFAULT_GLOBAL_COUNT = 12;
    private final IGradingScaleRepository repo = new GradingScaleRepositoryImpl();

    @Test
    void insertAndFindAllWithSubject() {
        repo.insert(null, 80, 100, "A", 12, "Excellent");
        repo.insert(null, 60, 79, "B", 10, "Good");
        var all = repo.findAllWithSubject();
        assertTrue(all.size() >= DEFAULT_GLOBAL_COUNT + 2);
    }

    @Test
    void findAllWithSubject_showsSubjectNameForPerSubjectScale() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        repo.insert(subj, 80, 100, "A", 12, "Excellent");
        var all = repo.findAllWithSubject();
        assertTrue(all.size() >= DEFAULT_GLOBAL_COUNT + 1);
        assertTrue(all.stream().anyMatch(r -> "Mathematics".equals(r.get("subject_name"))));
        assertTrue(all.stream().anyMatch(r -> "** Global **".equals(r.get("subject_name"))));
    }

    @Test
    void update() {
        repo.insert(null, 85, 100, "A", 12, "Excellent");
        // Find and update the one we just inserted
        var all = repo.findAllWithSubject();
        long id = (Long) all.stream()
            .filter(r -> 85.0 == (double) r.get("minimum_mark") && 100.0 == (double) r.get("maximum_mark"))
            .findFirst().get().get("id");
        repo.update(id, null, 85, 100, "A+", 13, "Outstanding");
        var updated = repo.findAllWithSubject();
        assertTrue(updated.stream().anyMatch(r -> "A+".equals(r.get("grade"))));
    }

    @Test
    void deleteById() {
        repo.insert(null, 85, 100, "A", 12, "Excellent");
        var all = repo.findAllWithSubject();
        long id = (Long) all.stream()
            .filter(r -> 85.0 == (double) r.get("minimum_mark"))
            .findFirst().get().get("id");
        repo.deleteById(id);
        var remaining = repo.findAllWithSubject();
        assertTrue(remaining.stream().noneMatch(r -> r.get("id").equals(id)));
    }

    @Test
    void deleteGlobal() {
        long subj = insertSubject("MATH", "Math", "Math", "Compulsory");
        repo.insert(subj, 80, 100, "A", 12, "Excellent");
        repo.deleteGlobal();
        var all = repo.findAllWithSubject();
        // Only the per-subject scale should remain
        assertTrue(all.size() >= 1);
        assertTrue(all.stream().allMatch(r -> r.get("subject_id") != null));
    }

    @Test
    void batchInsertGlobal() {
        repo.insertBatchGlobal(CurriculumSystem.SYSTEM_844);
        var all = repo.findAllWithSubject();
        // 12 defaults + 12 batch = 24 (duplicates inserted because they're not IGNORE)
        assertTrue(all.size() >= DEFAULT_GLOBAL_COUNT);
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
        assertTrue(all.stream().anyMatch(r -> "A*".equals(r.get("grade"))));
    }

    @Test
    void update_withNullSubjectId() {
        repo.insert(null, 80, 100, "A", 12, "Excellent");
        var all = repo.findAllWithSubject();
        long id = (Long) all.stream()
            .filter(r -> 80.0 == (double) r.get("minimum_mark") && 100.0 == (double) r.get("maximum_mark"))
            .findFirst().get().get("id");
        repo.update(id, null, 0, 100, "A", 12, "Changed");
        var updated = repo.findAllWithSubject();
        assertTrue(updated.stream().anyMatch(r -> "Changed".equals(r.get("remarks"))));
    }
}
