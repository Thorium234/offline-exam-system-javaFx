package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeacherSubjectRepositoryTest extends DatabaseTestBase {

    private final ITeacherSubjectRepository repo = new TeacherSubjectRepositoryImpl();

    @Test
    void insertAndFindByUserId() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher One", "teacher");
        repo.insert(user, subj, 1, "East");
        repo.insert(user, subj, 2, "West");

        var assignments = repo.findByUserId(user);
        assertEquals(2, assignments.size());
        assertEquals("Mathematics", assignments.get(0).get("subject_name"));
    }

    @Test
    void findByUserId_returnsEmptyForNoAssignments() {
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        assertTrue(repo.findByUserId(user).isEmpty());
    }

    @Test
    void findFormsByTeacherAndSubject() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        repo.insert(user, subj, 1, "East");
        repo.insert(user, subj, 2, "West");

        var forms = repo.findFormsByTeacherAndSubject(user, subj);
        assertEquals(2, forms.size());
        assertTrue(forms.contains(1));
        assertTrue(forms.contains(2));
    }

    @Test
    void findStreamsByTeacherAndSubjectAndForm() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        repo.insert(user, subj, 1, "East");
        repo.insert(user, subj, 1, "West");

        var streams = repo.findStreamsByTeacherAndSubjectAndForm(user, subj, 1);
        assertEquals(2, streams.size());
        assertTrue(streams.contains("East"));
        assertTrue(streams.contains("West"));
    }

    @Test
    void deleteById() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        repo.insert(user, subj, 1, "East");
        assertEquals(1, repo.findByUserId(user).size());
        repo.deleteById(1L);
        assertTrue(repo.findByUserId(user).isEmpty());
    }

    @Test
    void insertOrIgnore_duplicate() {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        repo.insert(user, subj, 1, "East");
        repo.insert(user, subj, 1, "East");
        assertEquals(1, repo.findByUserId(user).size());
    }

    @Test
    void findByUserId_ordersBySubjectFormStream() {
        long math = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long eng = insertSubject("ENG", "English", "Languages", "Compulsory");
        long user = insertUser("teacher1", "pass", "Teacher", "teacher");
        repo.insert(user, math, 1, "East");
        repo.insert(user, eng, 1, "West");
        var list = repo.findByUserId(user);
        assertEquals("English", list.get(0).get("subject_name"));
        assertEquals("Mathematics", list.get(1).get("subject_name"));
    }
}
