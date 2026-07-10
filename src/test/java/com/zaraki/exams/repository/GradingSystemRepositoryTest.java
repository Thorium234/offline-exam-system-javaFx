package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.model.GradingSystem;
import com.zaraki.exams.model.GradingSystemEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradingSystemRepositoryTest extends DatabaseTestBase {

    private final IGradingSystemRepository repo = new GradingSystemRepositoryImpl();

    @Test
    void insertAndFindAll() {
        repo.insertSystem(new GradingSystem("System A", "desc", false));
        repo.insertSystem(new GradingSystem("System B", "desc2", false));
        List<GradingSystem> all = repo.findAll();
        assertTrue(all.size() >= 2);
        assertTrue(all.stream().anyMatch(s -> "System A".equals(s.getSystemName())));
        assertTrue(all.stream().anyMatch(s -> "System B".equals(s.getSystemName())));
    }

    @Test
    void insertAndFindById() {
        long id = repo.insertSystem(new GradingSystem("FindMe", "test", false));
        GradingSystem found = repo.findById(id);
        assertNotNull(found);
        assertEquals("FindMe", found.getSystemName());
        assertEquals("test", found.getDescription());
        assertFalse(found.isActive());
    }

    @Test
    void updateSystem() {
        long id = repo.insertSystem(new GradingSystem("Old Name", "old desc", false));
        GradingSystem sys = repo.findById(id);
        sys.setSystemName("New Name");
        sys.setDescription("new desc");
        repo.updateSystem(sys);
        GradingSystem updated = repo.findById(id);
        assertEquals("New Name", updated.getSystemName());
        assertEquals("new desc", updated.getDescription());
    }

    @Test
    void deleteSystem() {
        long id = repo.insertSystem(new GradingSystem("ToDelete", "", false));
        assertNotNull(repo.findById(id));
        repo.deleteSystem(id);
        assertNull(repo.findById(id));
    }

    @Test
    void setActive_makesOnlyOneActive() {
        long id1 = repo.insertSystem(new GradingSystem("S1", "", true));
        long id2 = repo.insertSystem(new GradingSystem("S2", "", false));
        repo.setActive(id1);
        repo.setActive(id2);
        GradingSystem active = repo.findActive();
        assertNotNull(active);
        assertEquals(id2, active.getId());

        GradingSystem s1 = repo.findById(id1);
        assertFalse(s1.isActive());
    }

    @Test
    void insertAndFindEntries() {
        long sysId = repo.insertSystem(new GradingSystem("Grades", "", false));
        repo.insertEntry(new GradingSystemEntry(sysId, null, 0, 50, "D", 2, "Low"));
        repo.insertEntry(new GradingSystemEntry(sysId, null, 51, 100, "A", 12, "High"));

        List<GradingSystemEntry> entries = repo.findEntriesBySystem(sysId);
        assertEquals(2, entries.size());
    }

    @Test
    void insertEntry_withSubjectId() {
        long subjId = insertSubject("MATH", "Math", "Math", "Compulsory");
        long sysId = repo.insertSystem(new GradingSystem("Test", "", false));
        repo.insertEntry(new GradingSystemEntry(sysId, subjId, 80, 100, "A", 12, "Ex"));

        List<GradingSystemEntry> entries = repo.findEntriesBySystem(sysId);
        assertEquals(1, entries.size());
        assertEquals(subjId, entries.get(0).getSubjectId());
    }

    @Test
    void insertEntry_withNullSubjectId() {
        long sysId = repo.insertSystem(new GradingSystem("Test", "", false));
        repo.insertEntry(new GradingSystemEntry(sysId, null, 0, 100, "C", 6, ""));

        List<GradingSystemEntry> entries = repo.findEntriesBySystem(sysId);
        assertEquals(1, entries.size());
        assertNull(entries.get(0).getSubjectId());
    }

    @Test
    void updateEntry() {
        long sysId = repo.insertSystem(new GradingSystem("Test", "", false));
        long entryId = repo.insertEntry(new GradingSystemEntry(sysId, null, 0, 50, "D", 2, "Old"));

        GradingSystemEntry entry = repo.findEntriesBySystem(sysId).get(0);
        assertEquals("Old", entry.getRemarks());
        entry.setRemarks("Updated");
        entry.setGrade("D+");
        entry.setPoints(3);
        repo.updateEntry(entry);

        List<GradingSystemEntry> updated = repo.findEntriesBySystem(sysId);
        assertEquals("Updated", updated.get(0).getRemarks());
        assertEquals("D+", updated.get(0).getGrade());
        assertEquals(3, updated.get(0).getPoints());
    }

    @Test
    void deleteEntry() {
        long sysId = repo.insertSystem(new GradingSystem("Test", "", false));
        long entryId = repo.insertEntry(new GradingSystemEntry(sysId, null, 0, 50, "D", 2, ""));
        assertEquals(1, repo.findEntriesBySystem(sysId).size());
        repo.deleteEntry(entryId);
        assertEquals(0, repo.findEntriesBySystem(sysId).size());
    }

    @Test
    void insertBatchEntries() {
        long sysId = repo.insertSystem(new GradingSystem("Batch", "", false));
        List<GradingSystemEntry> batch = List.of(
            new GradingSystemEntry(sysId, null, 0, 30, "E", 1, ""),
            new GradingSystemEntry(sysId, null, 31, 60, "D", 3, ""),
            new GradingSystemEntry(sysId, null, 61, 80, "B", 9, ""),
            new GradingSystemEntry(sysId, null, 81, 100, "A", 12, "")
        );
        repo.insertBatchEntries(sysId, batch);
        assertEquals(4, repo.findEntriesBySystem(sysId).size());
    }

    @Test
    void cloneSystem() {
        long srcId = repo.insertSystem(new GradingSystem("Original", "src", false));
        repo.insertEntry(new GradingSystemEntry(srcId, null, 0, 50, "D", 2, "orig"));
        repo.insertEntry(new GradingSystemEntry(srcId, null, 51, 100, "A", 12, "orig"));

        repo.cloneSystem(srcId, "Cloned");
        List<GradingSystem> all = repo.findAll();
        GradingSystem cloned = all.stream()
            .filter(s -> "Cloned".equals(s.getSystemName()))
            .findFirst().orElse(null);
        assertNotNull(cloned);
        assertNotNull(cloned.getId());
        assertFalse(cloned.isActive());
        assertEquals("Cloned from Original", cloned.getDescription());

        List<GradingSystemEntry> clonedEntries = repo.findEntriesBySystem(cloned.getId());
        assertEquals(2, clonedEntries.size());
        assertTrue(clonedEntries.stream().allMatch(e -> e.getSystemId() == cloned.getId()));
    }

    @Test
    void findEntriesWithSubject_showsSubjectName() {
        long subjId = insertSubject("ENG", "English", "Lang", "Compulsory");
        long sysId = repo.insertSystem(new GradingSystem("Test", "", false));
        repo.insertEntry(new GradingSystemEntry(sysId, subjId, 70, 100, "A", 12, ""));
        repo.insertEntry(new GradingSystemEntry(sysId, null, 0, 60, "C", 6, ""));

        List<java.util.Map<String, Object>> rows = repo.findEntriesWithSubject(sysId);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "English".equals(r.get("subject_name"))));
        assertTrue(rows.stream().anyMatch(r -> "** Global **".equals(r.get("subject_name"))));
    }

    @Test
    void findActive_returnsNullWhenNoneActive() {
        assertNull(repo.findActive());
    }

    @Test
    void findAll_returnsEmptyWhenNoSystems() {
        List<GradingSystem> all = repo.findAll();
        assertTrue(all.isEmpty());
    }

    @Test
    void setActive_onNonexistent_throwsNoException() {
        assertDoesNotThrow(() -> repo.setActive(99999));
    }
}
