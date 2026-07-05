package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.model.Mark;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MarksRepositoryTest extends DatabaseTestBase {

    private final IMarksRepository repo = new MarksRepositoryImpl();

    @Test
    void batchInsertAndFindByExam() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long s1 = insertStudent("1001", "Alice", 1, "East");
        long s2 = insertStudent("1002", "Bob", 1, "East");

        Mark m1 = new Mark(exam, s1, subj, 85);
        m1.setGradeAchieved("A");
        m1.setPointsAchieved(12);
        Mark m2 = new Mark(exam, s2, subj, 70);
        m2.setGradeAchieved("B");
        m2.setPointsAchieved(10);

        repo.batchInsert(List.of(m1, m2));

        List<Mark> found = repo.findByExamId(exam);
        assertEquals(2, found.size());
    }

    @Test
    void findByExamStudentSubject() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");

        Mark m = new Mark(exam, student, subj, 85);
        m.setGradeAchieved("A");
        m.setPointsAchieved(12);
        repo.insert(m);

        Optional<Mark> found = repo.findByExamStudentSubject(exam, student, subj);
        assertTrue(found.isPresent());
        assertEquals(85.0, found.get().getScore());
    }

    @Test
    void findByExamStudentSubject_returnsEmptyForMissing() {
        Optional<Mark> found = repo.findByExamStudentSubject(999L, 999L, 999L);
        assertFalse(found.isPresent());
    }

    @Test
    void batchInsert_emptyListDoesNothing() {
        repo.batchInsert(List.of());
        assertTrue(repo.findByExamId(1L).isEmpty());
    }

    @Test
    void batchInsert_nullListDoesNothing() {
        repo.batchInsert(null);
    }

    @Test
    void deleteByExam() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");

        Mark m = new Mark(exam, student, subj, 85);
        repo.insert(m);

        repo.deleteByExam(exam);
        assertTrue(repo.findByExamId(exam).isEmpty());
    }

    @Test
    void singleInsert() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");

        Mark m = new Mark(exam, student, subj, 90);
        m.setGradeAchieved("A");
        m.setPointsAchieved(12);
        repo.insert(m);

        List<Mark> marks = repo.findByExamId(exam);
        assertEquals(1, marks.size());
        assertEquals(90.0, marks.get(0).getScore());
    }

    @Test
    void insertOrReplace_updatesExisting() {
        long subj = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");

        Mark m1 = new Mark(exam, student, subj, 85);
        m1.setGradeAchieved("B");
        m1.setPointsAchieved(10);
        repo.insert(m1);

        Mark m2 = new Mark(exam, student, subj, 95);
        m2.setGradeAchieved("A");
        m2.setPointsAchieved(12);
        repo.insert(m2);

        Optional<Mark> found = repo.findByExamStudentSubject(exam, student, subj);
        assertTrue(found.isPresent());
        assertEquals(95.0, found.get().getScore());
    }

    @Test
    void findByExamId_returnsEmptyForInvalid() {
        assertTrue(repo.findByExamId(999L).isEmpty());
    }
}
