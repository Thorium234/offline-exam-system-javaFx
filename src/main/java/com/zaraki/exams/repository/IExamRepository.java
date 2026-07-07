package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for CRUD operations on exam records.
 */
public interface IExamRepository {
    /** Returns all exams in ascending order by ID. */
    List<Map<String, Object>> findAll();
    /** Returns all exams in descending order by ID. */
    List<Map<String, Object>> findAllDesc();
    /** Inserts a new exam. */
    void insert(String year, String term, String series);
    /** Finds an exam by its ID. */
    Map<String, Object> findById(long id);
    /** Returns whether the exam has been released. */
    boolean isReleased(long examId);
    /** Marks the exam as released by the given user. */
    void release(long examId, String releasedBy);
    /** Returns the ID of the first (oldest) exam. */
    long getFirstExamId();
    /** Returns the ID of the exam immediately before the given one. */
    long getPreviousExamId(long currentExamId);
    /** Returns the most recent exam IDs up to the given limit. */
    List<Long> findLatestIds(int limit);
    /** Returns the maximum marks (out_of) across all subjects in the exam. */
    int getMaxMarks(long examId);
    /** Updates exam fields. */
    void update(long id, String academicYear, String term, String examSeries);
    /** Deletes an exam by ID. */
    void delete(long id);
}
