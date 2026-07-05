package com.zaraki.exams.repository;

import com.zaraki.exams.config.CurriculumSystem;
import java.util.List;
import java.util.Map;

public interface IGradingScaleRepository {
    List<Map<String, Object>> findAllWithSubject();
    List<Map<String, Object>> findAllSubjectsForCombo();
    long insert(Long subjectId, double min, double max, String grade, int points, String remarks);
    void deleteGlobal();
    void update(long id, Long subjectId, double min, double max, String grade, int points, String remarks);
    void deleteById(long id);
    void insertBatchGlobal(CurriculumSystem curriculum);
}
