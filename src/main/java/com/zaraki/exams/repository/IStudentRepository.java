package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IStudentRepository {
    List<Map<String, Object>> findAllActive();
    List<Map<String, Object>> search(String query, int limit, int offset);
    int searchCount(String query);
    void insert(String admission, String name, int form, String stream);
    void updatePhoto(long studentId, byte[] photoBytes);
    byte[] getPhoto(long studentId);
    Set<Long> getEnrolledSubjectIds(long studentId);
    void saveSubjects(long studentId, Map<Long, Boolean> subjectSelections);
    int countByFormStream(int form, String stream);
    Map<String, Object> findById(long id);
    List<Map<String, Object>> findByFormStream(int form, String stream);
    List<Map<String, Object>> findByFormStreamWithMarks(long examId, long subjectId, int form, String stream);
    int countByFormStreamStatus(int form, String stream);
    void update(long id, String admissionNumber, String fullName, int form, String stream);
    void deallocate(long studentId);
    void restore(long studentId);
    List<Map<String, Object>> findAllDeallocated();
    int countByFormStreamWithPrefix(String prefix, int form, String stream);
    void batchRestore(Set<Long> ids);
    void batchPermanentDelete(Set<Long> ids);
}
