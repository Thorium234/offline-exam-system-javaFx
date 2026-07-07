package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for student records (admission, personal info, photos, enrollment).
 */
public interface IStudentRepository {
    /** Returns all active (non-deallocated) students. */
    List<Map<String, Object>> findAllActive();
    /** Searches by admission number or name with pagination. */
    List<Map<String, Object>> search(String query, int limit, int offset);
    /** Returns total count matching the search query. */
    int searchCount(String query);
    /** Creates a new student record. */
    void insert(String admission, String name, int form, String stream);
    /** Updates the photo BLOB for a student. */
    void updatePhoto(long studentId, byte[] photoBytes);
    /** Retrieves the photo BLOB for a student. */
    byte[] getPhoto(long studentId);
    /** Returns the set of subject IDs the student is enrolled in. */
    Set<Long> getEnrolledSubjectIds(long studentId);
    /** Saves subject enrollment selections (upsert). */
    void saveSubjects(long studentId, Map<Long, Boolean> subjectSelections);
    /** Counts students in a given form and stream. */
    int countByFormStream(int form, String stream);
    /** Finds a student by ID. */
    Map<String, Object> findById(long id);
    /** Finds students by form and stream. */
    List<Map<String, Object>> findByFormStream(int form, String stream);
    /** Finds students with their marks for a specific exam/subject/form/stream. */
    List<Map<String, Object>> findByFormStreamWithMarks(long examId, long subjectId, int form, String stream);
    /** Counts students by form, stream, and status. */
    int countByFormStreamStatus(int form, String stream);
    /** Updates student fields. */
    void update(long id, String admissionNumber, String fullName, int form, String stream);
    /** Soft-deletes a student (sets deallocated=1). */
    void deallocate(long studentId);
    /** Restores a soft-deleted student (sets deallocated=0). */
    void restore(long studentId);
    /** Returns all soft-deleted students. */
    List<Map<String, Object>> findAllDeallocated();
    /** Counts students by form, stream, and admission-number prefix. */
    int countByFormStreamWithPrefix(String prefix, int form, String stream);
    /** Batch restore soft-deleted students by IDs. */
    void batchRestore(Set<Long> ids);
    /** Batch permanently delete soft-deleted students by IDs. */
    void batchPermanentDelete(Set<Long> ids);
}
