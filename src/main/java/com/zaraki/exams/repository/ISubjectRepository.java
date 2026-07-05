package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

public interface ISubjectRepository {
    List<Map<String, Object>> findAll();
    List<Map<String, Object>> findAllSimple();
    void insert(String code, String name, String department, String grouping);
    void deleteByCode(String code);
    String getName(long subjectId);
    Map<String, Object> findById(long id);
    List<Map<String, Object>> findByTeacher(long userId);
    List<Map<String, Object>> findByFormStreamWithMarksCount(long examId, int form, String stream);
}
