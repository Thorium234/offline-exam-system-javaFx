package com.zaraki.exams.repository;

import com.zaraki.exams.model.Mark;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IMarksRepository {
    void insert(Mark mark);
    void batchInsert(Collection<Mark> marks);
    List<Mark> findByExamId(long examId);
    Optional<Mark> findByExamStudentSubject(long examId, long studentId, long subjectId);
    void deleteByExam(long examId);
}
