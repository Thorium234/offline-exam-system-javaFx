package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamRepositoryTest extends DatabaseTestBase {

    private final IStreamRepository repo = new StreamRepositoryImpl();

    @Test
    void insertAndFindAllNames() {
        repo.insert(1, "East");
        repo.insert(1, "West");
        var names = repo.findAllNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("East"));
        assertTrue(names.contains("West"));
    }

    @Test
    void findAllWithStudentCount() {
        repo.insert(1, "East");
        insertStudent("1001", "Alice", 1, "East");
        insertStudent("1002", "Bob", 1, "East");
        var streams = repo.findAllWithStudentCount();
        assertEquals(1, streams.size());
        assertEquals(2, streams.get(0).get("cnt"));
    }

    @Test
    void findAllWithStudentCount_countsOnlyActive() {
        repo.insert(1, "East");
        insertStudent("1001", "Alice", 1, "East");
        long id = insertStudent("1002", "Bob", 1, "East");
        repo.updateStudentsStreamToGeneral(1, "East");

        var streams = repo.findAllWithStudentCount();
        assertEquals(0, streams.get(0).get("cnt"));
    }

    @Test
    void delete() {
        repo.insert(1, "East");
        assertFalse(repo.findAllNames().isEmpty());
        repo.delete(1, "East");
        assertTrue(repo.findAllNames().isEmpty());
    }

    @Test
    void insertOrIgnore_duplicate() {
        repo.insert(1, "East");
        repo.insert(1, "East");
        assertEquals(1, repo.findAllNames().size());
    }

    @Test
    void updateStudentsStreamToGeneral() {
        insertStudent("1001", "Alice", 1, "East");
        repo.updateStudentsStreamToGeneral(1, "East");
        var students = new StudentRepositoryImpl().findByFormStream(1, "General");
        assertEquals(1, students.size());
    }

    @Test
    void findAllForms() {
        repo.insert(1, "East");
        repo.insert(2, "West");
        var forms = repo.findAllForms();
        assertEquals(2, forms.size());
        assertTrue(forms.contains(1));
        assertTrue(forms.contains(2));
    }

    @Test
    void findAllDistinctForms() {
        repo.insert(1, "East");
        repo.insert(1, "West");
        repo.insert(2, "East");
        assertEquals(2, repo.findAllDistinctForms().size());
    }
}
