package com.zaraki.exams.repository;

import com.zaraki.exams.config.CurriculumSystem;
import java.util.List;
import java.util.Map;

/**
 * Repository for grading scale definitions.
 */
public interface IGradingScaleRepository {
    /** Returns all grading scales with subject names. */
    List<Map<String, Object>> findAllWithSubject();
    /** Returns all subjects for grading scale assignment combos. */
    List<Map<String, Object>> findAllSubjectsForCombo();
    /** Inserts a grading scale and returns its generated ID. */
    long insert(Long subjectId, double min, double max, String grade, int points, String remarks);
    /** Deletes all global (non-subject-specific) grading scales. */
    void deleteGlobal();
    /** Updates a grading scale entry. */
    void update(long id, Long subjectId, double min, double max, String grade, int points, String remarks);
    /** Deletes a grading scale by ID. */
    void deleteById(long id);
    /** Inserts a batch of default global scales for the given curriculum. */
    void insertBatchGlobal(CurriculumSystem curriculum);
}
