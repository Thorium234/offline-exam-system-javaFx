package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IStreamRepository {
    List<Map<String, Object>> findAllWithStudentCount();
    Set<String> findAllNames();
    void insert(int form, String stream);
    void delete(int form, String stream);
    void updateStudentsStreamToGeneral(int form, String stream);
    Set<Integer> findAllForms();
    Set<Integer> findAllDistinctForms();
}
