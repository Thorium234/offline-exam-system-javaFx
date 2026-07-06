package com.zaraki.exams.reporting;

import com.zaraki.exams.DatabaseTestBase;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardGeneratorTest extends DatabaseTestBase {

    @TempDir
    static Path tempDir;

    private long examId;
    private long studentId;
    private long subjectId;

    @BeforeEach
    void setupData() {
        subjectId = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        insertSubject("ENG", "English", "Languages", "Compulsory");
        studentId = insertStudent("1001", "Test Student", 1, "East");
        insertStudent("1002", "Second Student", 1, "East");
        examId = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(examId, subjectId, 100);
        insertExamSubject(examId, 2L, 100);
        insertMark(examId, studentId, subjectId, 85, "A", 12);
        insertMark(examId, studentId, 2L, 70, "B", 10);
        insertMark(examId, 2L, subjectId, 60, "B", 10);
        insertMark(examId, 2L, 2L, 50, "C", 8);
    }

    @Test
    void generateStudentReport_containsStudentName() throws IOException {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_single.pdf");
        gen.generateStudentReport(examId, studentId, output);
        assertTrue(Files.size(output) > 0);

        String text = extractPdfText(output);
        assertTrue(text.contains("Test Student"), "PDF should contain student name");
        assertTrue(text.contains("1001"), "PDF should contain admission number");
    }

    @Test
    void generateStudentReport_containsSubjectTable() throws IOException {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_subjects.pdf");
        gen.generateStudentReport(examId, studentId, output);

        String text = extractPdfText(output);
        assertTrue(text.contains("Subject"), "PDF should contain Subject header");
        assertTrue(text.contains("Mathematics"), "PDF should contain subject name");
        assertTrue(text.contains("Score"), "PDF should contain Score header");
    }

    @Test
    void generateStudentReport_containsSummary() throws IOException {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_summary.pdf");
        gen.generateStudentReport(examId, studentId, output);

        String text = extractPdfText(output);
        assertTrue(text.contains("SUMMARY"), "PDF should contain SUMMARY block");
        assertTrue(text.contains("Total Marks"), "PDF should contain total marks");
        assertTrue(text.contains("Total Points"), "PDF should contain total points");
    }

    @Test
    void generateBulkStudentReports_containsStudentNames() throws IOException {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_bulk.pdf");
        gen.generateBulkStudentReports(examId, "stream", "East", output);
        assertTrue(Files.size(output) > 0);

        String text = extractPdfText(output);
        assertTrue(text.contains("Test Student"), "Bulk PDF should contain first student");
        assertTrue(text.contains("Second Student"), "Bulk PDF should contain second student");
    }

    @Test
    void generateStudentReport_withNoMarks_doesNotThrow() {
        ReportCardGenerator gen = new ReportCardGenerator();
        long noMarksStudent = insertStudent("9999", "No Marks Student", 2, "West");
        Path output = tempDir.resolve("test_report_empty.pdf");
        gen.generateStudentReport(examId, noMarksStudent, output);
        assertTrue(Files.exists(output));
    }

    @Test
    void generateStudentReport_withNoExamSubjects_doesNotThrow() {
        long noSubjExam = insertExam("2026", "Term 2", "Mid Term");
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_no_subjects.pdf");
        gen.generateStudentReport(noSubjExam, studentId, output);
        assertTrue(Files.exists(output));
    }

    static String extractPdfText(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            PdfReader reader = new PdfReader(input);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                text.append(extractor.getTextFromPage(i));
            }
            reader.close();
            return text.toString();
        }
    }
}
