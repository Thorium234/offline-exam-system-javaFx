package com.zaraki.exams.service;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.database.DatabaseEngine;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelServiceTest extends DatabaseTestBase {

    private final IExcelService excelService = new ExcelServiceImpl();

    @Test
    void generateStudentTemplate_createsValidXlsx() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        Path tmp = Files.createTempFile("students_template", ".xlsx");
        try {
            excelService.generateStudentTemplate(tmp);
            assertTrue(Files.size(tmp) > 0);
            try (Workbook wb = new XSSFWorkbook(tmp.toFile())) {
                Sheet sheet = wb.getSheetAt(0);
                assertEquals("Students", sheet.getSheetName());
                Row header = sheet.getRow(0);
                assertNotNull(header);
                assertEquals("Admission No.", header.getCell(0).getStringCellValue());
                assertEquals("Full Name", header.getCell(1).getStringCellValue());
                assertEquals("Form", header.getCell(2).getStringCellValue());
                assertEquals("Stream", header.getCell(3).getStringCellValue());
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void generateStudentListExcel_includesStudentData() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        insertStudent("1001", "Alice Wanjiku", 1, "East");
        insertStudent("1002", "Bob Kiprop", 1, "East");
        Path tmp = Files.createTempFile("student_list", ".xlsx");
        try {
            excelService.generateStudentListExcel(tmp, "stream", "East");
            assertTrue(Files.size(tmp) > 0);
            try (Workbook wb = new XSSFWorkbook(tmp.toFile())) {
                Sheet sheet = wb.getSheetAt(0);
                assertTrue(sheet.getPhysicalNumberOfRows() >= 2);
                Row r1 = sheet.getRow(1);
                assertNotNull(r1);
                assertEquals("Alice Wanjiku", r1.getCell(2).getStringCellValue());
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processStudentUpload_insertsNewStudents() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Admission No.");
            header.createCell(1).setCellValue("Full Name");
            header.createCell(2).setCellValue("Form");
            header.createCell(3).setCellValue("Stream");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026001");
            r1.createCell(1).setCellValue("Test Student");
            r1.createCell(2).setCellValue("1");
            r1.createCell(3).setCellValue("East");
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("import_students", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processStudentUpload(tmp);
            assertEquals(1, result.totalRows());
            assertEquals(1, result.inserted());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processStudentUpload_updatesExistingStudents() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        insertStudent("2026001", "Original Name", 1, "East");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Admission No.");
            header.createCell(1).setCellValue("Full Name");
            header.createCell(2).setCellValue("Form");
            header.createCell(3).setCellValue("Stream");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026001");
            r1.createCell(1).setCellValue("Updated Name");
            r1.createCell(2).setCellValue("2");
            r1.createCell(3).setCellValue("West");
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("update_students", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processStudentUpload(tmp);
            assertEquals(1, result.totalRows());
            assertEquals(1, result.updated());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void generateTemplate_createsMarksTemplateWithCorrectHeaders() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 100);
        insertStudent("S001", "Test Student", 1, "East");
        insertStream(1, "East");
        insertStreamSubject(1, "East", subj);
        Path tmp = Files.createTempFile("marks_template", ".xlsx");
        try {
            excelService.generateTemplate(tmp, exam, 1, "East");
            assertTrue(Files.size(tmp) > 0);
            try (Workbook wb = new XSSFWorkbook(tmp.toFile())) {
                Sheet sheet = wb.getSheetAt(0);
                Row headerRow = sheet.getRow(2);
                assertNotNull(headerRow);
                assertEquals("Admission No.", headerRow.getCell(0).getStringCellValue());
                assertEquals("Student Name", headerRow.getCell(1).getStringCellValue());
                assertEquals("Mathematics", headerRow.getCell(2).getStringCellValue());
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processUpload_insertsMarksCorrectly() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 100);
        long student = insertStudent("S001", "Test Student", 1, "East");
        insertStream(1, "East");
        insertStreamSubject(1, "East", subj);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Form 1 - East");
            Row infoRow = sheet.createRow(0);
            infoRow.createCell(0).setCellValue("EXAM: 2026 Term 1 End Term");
            Row headerRow = sheet.createRow(2);
            headerRow.createCell(0).setCellValue("Admission No.");
            headerRow.createCell(1).setCellValue("Student Name");
            headerRow.createCell(2).setCellValue("Mathematics");
            Row dataRow = sheet.createRow(3);
            dataRow.createCell(0).setCellValue("S001");
            dataRow.createCell(1).setCellValue("Test Student");
            dataRow.createCell(2).setCellValue(85);
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("marks_upload", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processUpload(tmp, exam, 1, "East");
            assertEquals(1, result.totalRows());
            assertEquals(1, result.marksInserted());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processUpload_autoRegistersNewStudent() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 100);
        insertStream(1, "East");
        insertStreamSubject(1, "East", subj);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Form 1 - East");
            Row infoRow = sheet.createRow(0);
            infoRow.createCell(0).setCellValue("EXAM: 2026 Term 1 End Term");
            Row headerRow = sheet.createRow(2);
            headerRow.createCell(0).setCellValue("Admission No.");
            headerRow.createCell(1).setCellValue("Student Name");
            headerRow.createCell(2).setCellValue("Mathematics");
            Row dataRow = sheet.createRow(3);
            dataRow.createCell(0).setCellValue("NEW001");
            dataRow.createCell(1).setCellValue("New Student");
            dataRow.createCell(2).setCellValue(90);
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("auto_reg", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processUpload(tmp, exam, 1, "East");
            assertEquals(1, result.marksInserted());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processUpload_emptyRowsAreSkipped() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 100);
        insertStudent("S001", "Test Student", 1, "East");
        insertStream(1, "East");
        insertStreamSubject(1, "East", subj);
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Form 1 - East");
            Row infoRow = sheet.createRow(0);
            infoRow.createCell(0).setCellValue("EXAM: 2026 Term 1 End Term");
            Row headerRow = sheet.createRow(2);
            headerRow.createCell(0).setCellValue("Admission No.");
            headerRow.createCell(1).setCellValue("Student Name");
            headerRow.createCell(2).setCellValue("Mathematics");
            sheet.createRow(3); // empty row
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("empty_rows", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processUpload(tmp, exam, 1, "East");
            assertEquals(0, result.totalRows());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processSubjectUpload_singleSheetUpload() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj, 100);
        long student = insertStudent("S001", "Test Student", 1, "East");
        insertStream(1, "East");
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(subj + "_MATH");
            Row h0 = sheet.createRow(0);
            h0.createCell(0).setCellValue("EXAM: 2026 Term 1 End Term");
            Row h1 = sheet.createRow(1);
            h1.createCell(0).setCellValue("SUBJECT: Mathematics");
            Row h3 = sheet.createRow(3);
            h3.createCell(0).setCellValue("Admission No.");
            h3.createCell(1).setCellValue("Student Name");
            h3.createCell(2).setCellValue("Mathematics Marks");
            Row d = sheet.createRow(4);
            d.createCell(0).setCellValue("S001");
            d.createCell(1).setCellValue("Test Student");
            d.createCell(2).setCellValue(85);
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("subject_upload", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processSubjectUpload(tmp, exam, subj, 1, "East");
            assertEquals(1, result.marksInserted());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void generateTeacherMultiSheetTemplate_createsMultipleSheets() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        long user = insertUser("teacher1", "pass", "Teacher One", "teacher");
        insertTeacherSubject(user, subj, 1, "East");
        insertStudent("S001", "Student A", 1, "East");
        insertStream(1, "East");
        insertExamSubject(exam, subj, 100);

        Path tmp = Files.createTempFile("multisheet_template", ".xlsx");
        try {
            excelService.generateTeacherMultiSheetTemplate(tmp, exam, user);
            assertTrue(Files.size(tmp) > 0);
            try (Workbook wb = new XSSFWorkbook(tmp.toFile())) {
                assertEquals(1, wb.getNumberOfSheets());
                Sheet sheet = wb.getSheetAt(0);
                Row headerRow = sheet.getRow(3);
                assertEquals("Admission No.", headerRow.getCell(0).getStringCellValue());
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processTeacherMultiSheetUpload_parsesAllSheets() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        long subj1 = insertSubject("MATH", "Mathematics", "Mathematics", "Compulsory");
        long subj2 = insertSubject("ENG", "English", "Languages", "Compulsory");
        long exam = insertExam("2026", "Term 1", "End Term");
        insertExamSubject(exam, subj1, 100);
        insertExamSubject(exam, subj2, 100);
        long student = insertStudent("S001", "Test Student", 1, "East");
        insertStream(1, "East");
        insertGradeScale(null, 80, 100, "A", 12, "Excellent");
        insertGradeScale(null, 0, 79, "B", 10, "Good");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            for (long subjId : new long[]{subj1, subj2}) {
                String sheetName = "S" + subjId + "_Subject_F1_East";
                Sheet sheet = wb.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);
                Row h0 = sheet.createRow(0);
                h0.createCell(0).setCellValue("EXAM: 2026 Term 1 End Term");
                Row h1 = sheet.createRow(1);
                h1.createCell(0).setCellValue("SUBJECT: Subject | Form 1 - East");
                Row h3 = sheet.createRow(3);
                h3.createCell(0).setCellValue("Admission No.");
                h3.createCell(1).setCellValue("Student Name");
                h3.createCell(2).setCellValue("Subject Marks");
                Row d = sheet.createRow(4);
                d.createCell(0).setCellValue("S001");
                d.createCell(1).setCellValue("Test Student");
                d.createCell(2).setCellValue(85);
            }
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("multi_upload", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processTeacherMultiSheetUpload(tmp, exam, 1, "East");
            assertEquals(2, result.marksInserted());
            assertEquals(0, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void processStudentUpload_invalidForm_reportsError() throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Admission No.");
            header.createCell(1).setCellValue("Full Name");
            header.createCell(2).setCellValue("Form");
            header.createCell(3).setCellValue("Stream");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026001");
            r1.createCell(1).setCellValue("Test Student");
            r1.createCell(2).setCellValue("invalid");
            r1.createCell(3).setCellValue("East");
            wb.write(bos);
        }
        Path tmp = Files.createTempFile("invalid_form", ".xlsx");
        try {
            Files.write(tmp, bos.toByteArray());
            var result = excelService.processStudentUpload(tmp);
            assertEquals(1, result.totalRows());
            assertEquals(1, result.errors());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
