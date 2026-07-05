package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ITeacherSubjectRepository {
    Set<Integer> findFormsByTeacherAndSubject(long userId, long subjectId);
    Set<String> findStreamsByTeacherAndSubjectAndForm(long userId, long subjectId, int form);
    List<Map<String, Object>> findByUserId(long userId);
    void insert(long userId, long subjectId, int form, String stream);
    void deleteById(long id);
}
