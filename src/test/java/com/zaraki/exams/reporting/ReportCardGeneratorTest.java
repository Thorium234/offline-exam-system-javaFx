package com.zaraki.exams.reporting;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.database.InMemoryDbExtension;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardGeneratorTest extends DatabaseTestBase {

    @TempDir
    static Path tempDir;

    private static long examId;
    private static long studentId;
    private static long subjectId;

    @BeforeAll
    static void setupData() {
        InMemoryDbExtension dbExt = dbExtension;
        subjectId = insertSubjectStatic("MATH", "Mathematics", "Mathematics", "Compulsory");
        insertSubjectStatic("ENG", "English", "Languages", "Compulsory");
        studentId = insertStudentStatic("1001", "Test Student", 1, "East");
        insertStudentStatic("1002", "Second Student", 1, "East");
        examId = insertExamStatic("2026", "Term 1", "End Term");
        insertExamSubjectStatic(examId, subjectId, 100);
        insertExamSubjectStatic(examId, 2L, 100);
        insertGradeScaleStatic(null, 80, 100, "A", 12, "Excellent");
        insertGradeScaleStatic(null, 60, 79, "B", 10, "Good");
        insertGradeScaleStatic(null, 40, 59, "C", 8, "Average");
        insertGradeScaleStatic(null, 0, 39, "D", 4, "Below Average");
        insertMarkStatic(examId, studentId, subjectId, 85, "A", 12);
        insertMarkStatic(examId, studentId, 2L, 70, "B", 10);
        insertMarkStatic(examId, 2L, subjectId, 60, "B", 10);
        insertMarkStatic(examId, 2L, 2L, 50, "C", 8);
    }

    private static long insertSubjectStatic(String code, String name, String dept, String grouping) {
        return new com.zaraki.exams.repository.SubjectRepositoryImpl().findAll().stream()
            .filter(m -> m.get("subject_code").equals(code))
            .mapToLong(m -> (Long) m.get("id"))
            .findFirst().orElseGet(() -> {
                try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
                     var ps = conn.prepareStatement(
                         "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)",
                         java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code);
                    ps.setString(2, name);
                    ps.setString(3, dept);
                    ps.setString(4, grouping);
                    ps.executeUpdate();
                    var rs = ps.getGeneratedKeys();
                    return rs.next() ? rs.getLong(1) : -1;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private static long insertStudentStatic(String adm, String name, int form, String stream) {
        try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, adm);
            ps.setString(2, name);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long insertExamStatic(String year, String term, String series) {
        try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, year);
            ps.setString(2, term);
            ps.setString(3, series);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void insertExamSubjectStatic(long eId, long sId, int outOf) {
        try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (?,?,?)")) {
            ps.setLong(1, eId);
            ps.setLong(2, sId);
            ps.setInt(3, outOf);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void insertGradeScaleStatic(Long subjectId, double min, double max, String grade, int points, String remarks) {
        try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?,?,?,?,?,?)")) {
            if (subjectId == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setLong(1, subjectId);
            ps.setDouble(2, min);
            ps.setDouble(3, max);
            ps.setString(4, grade);
            ps.setInt(5, points);
            ps.setString(6, remarks);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void insertMarkStatic(long examId, long studentId, long subjectId, double score, String grade, int points) {
        try (var conn = com.zaraki.exams.database.DatabaseEngine.getInstance().getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ps.setLong(3, subjectId);
            ps.setDouble(4, score);
            ps.setString(5, grade);
            ps.setInt(6, points);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        long noMarksStudent = insertStudentStatic("9999", "No Marks Student", 2, "West");
        Path output = tempDir.resolve("test_report_empty.pdf");
        gen.generateStudentReport(examId, noMarksStudent, output);
        assertTrue(Files.exists(output));
    }

    @Test
    void generateGroupReport_containsStudentData() throws IOException {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = tempDir.resolve("test_report_group.pdf");
        gen.generateGroupReport(examId, "stream", "East", output);
        assertTrue(Files.size(output) > 0);

        String text = extractPdfText(output);
        assertTrue(text.contains("MERIT LIST"), "Group PDF should contain merit list title");
    }

    private String extractPdfText(Path pdfPath) throws IOException {
        PdfReader reader = new PdfReader(Files.newInputStream(pdfPath));
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i));
        }
        reader.close();
        return sb.toString();
    }
}
