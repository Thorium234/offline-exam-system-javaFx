package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for subject definitions (codes, names, departments, groupings).
 */
public interface ISubjectRepository {
    /** Returns all subjects with all columns. */
    List<Map<String, Object>> findAll();
    /** Returns all subjects with limited columns (for combos). */
    List<Map<String, Object>> findAllSimple();
    /** Creates a new subject. */
    void insert(String code, String name, String department, String grouping);
    /** Deletes a subject by its code. */
    void deleteByCode(String code);
    /** Returns the display name for a subject ID. */
    String getName(long subjectId);
    /** Finds a subject by its ID. */
    Map<String, Object> findById(long id);
    /** Returns subjects assigned to a teacher. */
    List<Map<String, Object>> findByTeacher(long userId);
    /** Returns subjects with mark counts for a given exam/form/stream. */
    List<Map<String, Object>> findByFormStreamWithMarksCount(long examId, int form, String stream);
}
