package com.zaraki.exams.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Service for Excel operations: templates, upload processing, student import.
 */
public interface IExcelService {

    /** Result of importing marks from an Excel upload. */
    record ImportResult(int totalRows, int marksInserted, int errors, List<String> errorMessages) {}
    /** Result of importing student data from an Excel upload. */
    record StudentImportResult(int totalRows, int inserted, int updated, int errors, List<String> errorMessages) {}

    /** Generates a blank student registration template. */
    void generateStudentTemplate(Path outputPath);
    /** Generates an Excel listing of students filtered by column/value. */
    void generateStudentListExcel(Path outputPath, String filterCol, String filterValue);
    /** Processes a student upload Excel file, inserting/updating records. */
    StudentImportResult processStudentUpload(Path inputPath);
    /** Generates a single-sheet marks entry template for a form/stream. */
    void generateTemplate(Path outputPath, long examId, int form, String stream);
    /** Generates a subject-specific marks entry template. */
    void generateSubjectTemplate(Path outputPath, long examId, int form, String stream, long subjectId);
    /** Processes a subject-specific marks upload. */
    ImportResult processSubjectUpload(Path inputPath, long examId, long subjectId, int form, String stream);
    /** Generates a multi-sheet template for a teacher's assigned subjects. */
    void generateTeacherMultiSheetTemplate(Path outputPath, long examId, long userId);
    /** Processes a teacher's multi-sheet marks upload. */
    ImportResult processTeacherMultiSheetUpload(Path inputPath, long examId, int form, String stream);
    /** Processes a general marks upload (legacy format). */
    ImportResult processUpload(Path inputPath, long examId, int form, String stream);
}
