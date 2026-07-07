package com.zaraki.exams.repository;

import com.zaraki.exams.model.Mark;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for student mark (score) records.
 */
public interface IMarksRepository {
    /** Inserts a single mark record. */
    void insert(Mark mark);
    /** Batch-inserts marks inside a single transaction (500 per chunk). */
    void batchInsert(Collection<Mark> marks);
    /** Finds all marks for the given exam. */
    List<Mark> findByExamId(long examId);
    /** Finds a single mark by its composite key, if present. */
    Optional<Mark> findByExamStudentSubject(long examId, long studentId, long subjectId);
    /** Deletes all marks for the given exam. */
    void deleteByExam(long examId);
}
