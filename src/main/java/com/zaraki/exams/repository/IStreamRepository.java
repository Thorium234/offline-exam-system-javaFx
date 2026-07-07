package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for stream (form/stream combination) management.
 */
public interface IStreamRepository {
    /** Returns all streams with student counts. */
    List<Map<String, Object>> findAllWithStudentCount();
    /** Returns all distinct stream names, cached with 10-min TTL. */
    Set<String> findAllNames();
    /** Creates a new stream for the given form. */
    void insert(int form, String stream);
    /** Deletes a stream (moves students to General stream first). */
    void delete(int form, String stream);
    /** Moves students in the given form/stream to "General" stream. */
    void updateStudentsStreamToGeneral(int form, String stream);
    /** Returns all form levels that have streams. */
    Set<Integer> findAllForms();
    /** Returns all distinct form levels across all streams. */
    Set<Integer> findAllDistinctForms();
}
