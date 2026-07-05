package com.zaraki.exams.service;

import java.nio.file.Path;
import java.util.List;

public interface IExcelService {

    record ImportResult(int totalRows, int marksInserted, int errors, List<String> errorMessages) {}
    record StudentImportResult(int totalRows, int inserted, int updated, int errors, List<String> errorMessages) {}

    void generateStudentTemplate(Path outputPath);
    void generateStudentListExcel(Path outputPath, String filterCol, String filterValue);
    StudentImportResult processStudentUpload(Path inputPath);
    void generateTemplate(Path outputPath, long examId, int form, String stream);
    void generateSubjectTemplate(Path outputPath, long examId, int form, String stream, long subjectId);
    ImportResult processSubjectUpload(Path inputPath, long examId, long subjectId, int form, String stream);
    void generateTeacherMultiSheetTemplate(Path outputPath, long examId, long userId);
    ImportResult processTeacherMultiSheetUpload(Path inputPath, long examId, int form, String stream);
    ImportResult processUpload(Path inputPath, long examId, int form, String stream);
}
