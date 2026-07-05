package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StudentRepositoryTest extends DatabaseTestBase {

    private final IStudentRepository repo = new StudentRepositoryImpl();

    @Test
    void insertAndFindById() {
        repo.insert("1001", "Alice Wanjiku", 1, "East");
        Map<String, Object> found = repo.findById(1L);
        assertNotNull(found);
        assertEquals("1001", found.get("admission_number"));
        assertEquals("Alice Wanjiku", found.get("full_name"));
        assertEquals(1, found.get("form"));
        assertEquals("East", found.get("stream"));
    }

    @Test
    void updateStudent() {
        repo.insert("1001", "Alice Wanjiku", 1, "East");
        repo.update(1L, "1001", "Alice Wanjiku Updated", 2, "West");
        Map<String, Object> found = repo.findById(1L);
        assertEquals("Alice Wanjiku Updated", found.get("full_name"));
        assertEquals(2, found.get("form"));
        assertEquals("West", found.get("stream"));
    }

    @Test
    void findAllActive_returnsOnlyNonDeallocated() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        repo.deallocate(1L);
        var active = repo.findAllActive();
        assertEquals(1, active.size());
        assertEquals("1002", active.get(0).get("admission_number"));
    }

    @Test
    void deallocateAndRestore() {
        repo.insert("1001", "Alice", 1, "East");
        repo.deallocate(1L);
        assertTrue(repo.findAllActive().isEmpty());
        repo.restore(1L);
        assertEquals(1, repo.findAllActive().size());
    }

    @Test
    void searchByAdmissionNumber() {
        repo.insert("1001", "Alice Wanjiku", 1, "East");
        repo.insert("2001", "Bob Kiprop", 1, "East");
        var results = repo.search("1001", 10, 0);
        assertEquals(1, results.size());
        assertEquals("Alice Wanjiku", results.get(0).get("full_name"));
    }

    @Test
    void searchByName_usesLike() {
        repo.insert("1001", "Alice Wanjiku", 1, "East");
        repo.insert("1002", "Alice Kimani", 1, "West");
        repo.insert("1003", "Bob Kiprop", 1, "East");
        var results = repo.search("Alice", 10, 0);
        assertEquals(2, results.size());
    }

    @Test
    void searchCount() {
        repo.insert("1001", "Alice Wanjiku", 1, "East");
        repo.insert("1002", "Alice Kimani", 1, "West");
        assertEquals(2, repo.searchCount("Alice"));
        assertEquals(0, repo.searchCount("NonExistent"));
    }

    @Test
    void paginationWithOffsetAndLimit() {
        for (int i = 1; i <= 10; i++) {
            repo.insert("10" + String.format("%02d", i), "Student " + i, 1, "East");
        }
        var page1 = repo.search("Student", 3, 0);
        assertEquals(3, page1.size());
        var page2 = repo.search("Student", 3, 3);
        assertEquals(3, page2.size());
        assertNotEquals(page1.get(0).get("id"), page2.get(0).get("id"));
    }

    @Test
    void findById_returnsNullForMissing() {
        assertNull(repo.findById(999L));
    }

    @Test
    void findDeallocated_returnsOnlyDeallocatedStudents() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        repo.deallocate(1L);
        var deallocated = repo.findAllDeallocated();
        assertEquals(1, deallocated.size());
        assertEquals("1001", deallocated.get(0).get("admission_number"));
    }

    @Test
    void photoBlobStorageAndRetrieval() {
        repo.insert("1001", "Alice", 1, "East");
        byte[] photo = new byte[]{1, 2, 3, 4, 5};
        repo.updatePhoto(1L, photo);
        byte[] retrieved = repo.getPhoto(1L);
        assertArrayEquals(photo, retrieved);
    }

    @Test
    void getPhoto_returnsNullForNoPhoto() {
        repo.insert("1001", "Alice", 1, "East");
        assertNull(repo.getPhoto(1L));
    }

    @Test
    void getPhoto_returnsNullForMissingStudent() {
        assertNull(repo.getPhoto(999L));
    }

    @Test
    void subjectEnrollmentOperations() {
        long s1 = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long s2 = insertSubject("ENG", "English", "Languages", "Compulsory");
        repo.insert("1001", "Alice", 1, "East");
        Map<Long, Boolean> selections = Map.of(s1, true, s2, false);
        repo.saveSubjects(1L, selections);
        Set<Long> enrolled = repo.getEnrolledSubjectIds(1L);
        assertEquals(1, enrolled.size());
        assertTrue(enrolled.contains(s1));
    }

    @Test
    void findByFormStream() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        repo.insert("1003", "Charlie", 2, "West");
        var form1East = repo.findByFormStream(1, "East");
        assertEquals(2, form1East.size());
        var form2West = repo.findByFormStream(2, "West");
        assertEquals(1, form2West.size());
    }

    @Test
    void findByFormStreamWithMarks() {
        long s1 = insertSubject("MATH", "Mathematics", "Math", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        insertMark(exam, 1L, s1, 85.0, "A", 12);
        insertMark(exam, 2L, s1, 70.0, "B", 10);
        var results = repo.findByFormStreamWithMarks(exam, s1, 1, "East");
        assertEquals(2, results.size());
    }

    @Test
    void countByFormStream() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        assertEquals(2, repo.countByFormStream(1, "East"));
        assertEquals(0, repo.countByFormStream(1, "West"));
    }

    @Test
    void countByFormStreamWithPrefix() {
        repo.insert("S1001", "Alice", 1, "East");
        repo.insert("S1002", "Bob", 1, "East");
        repo.insert("T2001", "Charlie", 1, "East");
        assertEquals(2, repo.countByFormStreamWithPrefix("S", 1, "East"));
    }

    @Test
    void batchRestore() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        repo.insert("1003", "Charlie", 1, "East");
        repo.deallocate(1L);
        repo.deallocate(2L);
        repo.batchRestore(Set.of(1L, 2L));
        assertEquals(3, repo.findAllActive().size());
    }

    @Test
    void batchPermanentDelete() {
        repo.insert("1001", "Alice", 1, "East");
        repo.insert("1002", "Bob", 1, "East");
        repo.batchPermanentDelete(Set.of(1L));
        assertNull(repo.findById(1L));
        assertNotNull(repo.findById(2L));
    }

    @Test
    void duplicateAdmission_numberThrows() {
        repo.insert("1001", "Alice", 1, "East");
        assertThrows(RuntimeException.class, () -> repo.insert("1001", "Duplicate", 1, "East"));
    }

    @Test
    void countByFormStreamStatus() {
        repo.insert("1001", "Alice", 1, "East");
        assertEquals(1, repo.countByFormStreamStatus(1, "East"));
    }
}
