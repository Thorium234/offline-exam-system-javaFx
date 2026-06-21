package com.zaraki.exams.reporting;

import com.zaraki.exams.database.DatabaseEngine;
import org.junit.jupiter.api.*;
import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportCardGeneratorTest {

    private static final DatabaseEngine db = DatabaseEngine.getInstance();
    private static long examId;
    private static long studentId;
    private static long subjectId;
    private static long secondStudentId;

    @BeforeAll
    static void setup() throws SQLException {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");

            st.execute("DELETE FROM marks");
            st.execute("DELETE FROM exam_subjects");
            st.execute("DELETE FROM exam_subjects");
            st.execute("DELETE FROM exams");
            st.execute("DELETE FROM student_subjects");
            st.execute("DELETE FROM stream_subjects");
            st.execute("DELETE FROM subjects");
            st.execute("DELETE FROM grading_scales");
            st.execute("DELETE FROM students");

            st.execute("INSERT INTO subjects (id, subject_code, subject_name, department, grouping) VALUES (1, 'MATH', 'Mathematics', 'Mathematics', 'Compulsory')");
            st.execute("INSERT INTO subjects (id, subject_code, subject_name, department, grouping) VALUES (2, 'ENG', 'English', 'Languages', 'Compulsory')");
            subjectId = 1;

            st.execute("INSERT INTO students (id, admission_number, full_name, form, stream) VALUES (1, '1001', 'Test Student', 1, 'East')");
            st.execute("INSERT INTO students (id, admission_number, full_name, form, stream) VALUES (2, '1002', 'Second Student', 1, 'East')");
            studentId = 1;
            secondStudentId = 2;

            st.execute("INSERT INTO exams (id, academic_year, term, exam_series, released) VALUES (1, '2026', 'Term 1', 'End Term', 1)");
            st.execute("INSERT INTO exams (id, academic_year, term, exam_series, released) VALUES (2, '2026', 'Term 1', 'Mid Term', 1)");
            examId = 1;

            st.execute("INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (1, 1, 100)");
            st.execute("INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (1, 2, 100)");
            st.execute("INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (2, 1, 100)");
            st.execute("INSERT INTO exam_subjects (exam_id, subject_id, out_of) VALUES (2, 2, 100)");

            st.execute("INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL, 80, 100, 'A', 12, 'Excellent')");
            st.execute("INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL, 60, 79, 'B', 10, 'Good')");
            st.execute("INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL, 40, 59, 'C', 8, 'Average')");
            st.execute("INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL, 0, 39, 'D', 4, 'Below Average')");

            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (1, 1, 1, 85, 'A', 12)");
            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (1, 1, 2, 70, 'B', 10)");
            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (2, 1, 1, 75, 'B', 10)");
            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (2, 1, 2, 65, 'B', 10)");

            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (1, 2, 1, 60, 'B', 10)");
            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (1, 2, 2, 50, 'C', 8)");

            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    @Test
    @Order(1)
    void generateStudentReport_createsPdfSuccessfully() {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = new File("target/test_report_single.pdf").toPath();
        gen.generateStudentReport(examId, studentId, output);
        assertTrue(output.toFile().exists(), "PDF should exist");
        assertTrue(output.toFile().length() > 0, "PDF should not be empty");
    }

    @Test
    @Order(2)
    void generateBulkStudentReports_createsPdfSuccessfully() {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = new File("target/test_report_bulk.pdf").toPath();
        gen.generateBulkStudentReports(examId, "stream", "East", output);
        assertTrue(output.toFile().exists(), "Bulk PDF should exist");
        assertTrue(output.toFile().length() > 0, "Bulk PDF should not be empty");
    }

    @Test
    @Order(3)
    void generateStudentReport_withSingleMark_doesNotThrow() {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = new File("target/test_report_empty.pdf").toPath();
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.execute("INSERT INTO students (id, admission_number, full_name, form, stream) VALUES (99, '9999', 'No Marks Student', 2, 'West')");
            st.execute("INSERT INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (1, 99, 1, 0, 'D', 4)");
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) { throw new RuntimeException(e); }
        gen.generateStudentReport(examId, 99, output);
        assertTrue(output.toFile().exists());
    }

    @Test
    @Order(4)
    void generateGroupReport_createsPdfSuccessfully() {
        ReportCardGenerator gen = new ReportCardGenerator();
        Path output = new File("target/test_report_group.pdf").toPath();
        gen.generateGroupReport(examId, "stream", "East", output);
        assertTrue(output.toFile().exists(), "Group PDF should exist");
        assertTrue(output.toFile().length() > 0, "Group PDF should not be empty");
    }
}
