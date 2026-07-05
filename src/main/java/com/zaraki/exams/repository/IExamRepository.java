package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

public interface IExamRepository {
    List<Map<String, Object>> findAll();
    List<Map<String, Object>> findAllDesc();
    void insert(String year, String term, String series);
    Map<String, Object> findById(long id);
    boolean isReleased(long examId);
    void release(long examId, String releasedBy);
    long getFirstExamId();
    long getPreviousExamId(long currentExamId);
    List<Long> findLatestIds(int limit);
    int getMaxMarks(long examId);
    void update(long id, String academicYear, String term, String examSeries);
    void delete(long id);
}
