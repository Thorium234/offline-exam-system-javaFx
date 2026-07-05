package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubjectRepositoryTest extends DatabaseTestBase {

    private final ISubjectRepository repo = new SubjectRepositoryImpl();

    @Test
    void insertAndFindById() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        Map<String, Object> found = repo.findById(1L);
        assertNotNull(found);
        assertEquals("MATH", found.get("subject_code"));
        assertEquals("Mathematics", found.get("subject_name"));
    }

    @Test
    void findAll_returnsAllSubjects() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        repo.insert("ENG", "English", "Languages", "Compulsory");
        assertEquals(2, repo.findAll().size());
    }

    @Test
    void findAllSimple() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        var list = repo.findAllSimple();
        assertEquals(1, list.size());
        assertEquals("Mathematics", list.get(0).get("subject_name"));
    }

    @Test
    void deleteByCode() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        repo.deleteByCode("MATH");
        assertNull(repo.findById(1L));
    }

    @Test
    void getName() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        assertEquals("Mathematics", repo.getName(1L));
        assertEquals("Subject", repo.getName(999L));
    }

    @Test
    void findByTeacher() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        insertTeacherSubject(user, subj, 1, "East");
        var subjects = repo.findByTeacher(user);
        assertEquals(1, subjects.size());
        assertEquals("MATH", subjects.get(0).get("subject_code"));
    }

    @Test
    void findByTeacher_returnsEmptyForNoAssignments() {
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        assertTrue(repo.findByTeacher(user).isEmpty());
    }

    @Test
    void findByFormStreamWithMarksCount() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long student = insertStudent("1001", "Alice", 1, "East");
        insertStream(1, "East");
        insertStreamSubject(1, "East", subj);
        insertExamSubject(exam, subj, 100);
        insertMark(exam, student, subj, 85);

        var results = repo.findByFormStreamWithMarksCount(exam, 1, "East");
        assertEquals(1, results.size());
        assertEquals("MATH", results.get(0).get("subject_code"));
        assertEquals(1, results.get(0).get("mark_count"));
    }

    @Test
    void findByFormStreamWithMarksCount_noStreamSubjects_returnsAll() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        var results = repo.findByFormStreamWithMarksCount(exam, 1, "East");
        assertFalse(results.isEmpty());
    }

    @Test
    void findById_returnsNullForMissing() {
        assertNull(repo.findById(999L));
    }

    @Test
    void duplicateCode_throws() {
        repo.insert("MATH", "Mathematics", "Mathematics", "Compulsory");
        assertThrows(RuntimeException.class, () -> repo.insert("MATH", "Duplicate", "Math", "Elective"));
    }
}
