package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for teacher-subject-stream assignments.
 */
public interface ITeacherSubjectRepository {
    /** Returns the form levels a teacher teaches for a given subject. */
    Set<Integer> findFormsByTeacherAndSubject(long userId, long subjectId);
    /** Returns the streams a teacher teaches for a given subject and form. */
    Set<String> findStreamsByTeacherAndSubjectAndForm(long userId, long subjectId, int form);
    /** Returns all subject assignments for a teacher. */
    List<Map<String, Object>> findByUserId(long userId);
    /** Assigns a teacher to a subject/form/stream. */
    void insert(long userId, long subjectId, int form, String stream);
    /** Removes a teacher-subject assignment by ID. */
    void deleteById(long id);
}
