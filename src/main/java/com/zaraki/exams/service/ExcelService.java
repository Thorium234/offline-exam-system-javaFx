package com.zaraki.exams.service;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.Mark;
import com.zaraki.exams.repository.MarksRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class ExcelService {

    private final DatabaseEngine db;
    private final MarksRepository marksRepo;
    private final ExamAnalysisService analysisService;

    public ExcelService() {
        this.db = DatabaseEngine.getInstance();
        this.marksRepo = new MarksRepository();
        this.analysisService = new ExamAnalysisService();
    }

    public record ImportResult(int totalRows, int marksInserted, int errors, List<String> errorMessages) {}
    public record StudentImportResult(int totalRows, int inserted, int updated, int errors, List<String> errorMessages) {}

    public void generateStudentTemplate(Path outputPath) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Students");
            Row headerRow = sheet.createRow(0);
            String[] cols = {"Admission No.", "Full Name", "Form", "Stream"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(boldStyle(wb));
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.autoSizeColumn(3);
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate student template", e);
        }
    }

    public StudentImportResult processStudentUpload(Path inputPath) {
        List<String> errors = new ArrayList<>();
        int inserted = 0, updated = 0, totalRows = 0;

        try (Workbook wb = new XSSFWorkbook(inputPath.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null)
                return new StudentImportResult(0, 0, 0, 1, List.of("Header row not found."));

            try (Connection conn = db.getConnection();
                 PreparedStatement insertPs = conn.prepareStatement(
                     "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?, ?, ?, ?)");
                 PreparedStatement updatePs = conn.prepareStatement(
                     "UPDATE students SET full_name = ?, form = ?, stream = ? WHERE admission_number = ?")) {

                conn.setAutoCommit(false);
                try {
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        String adm = getCellString(row.getCell(0));
                        String name = getCellString(row.getCell(1));
                        String formStr = getCellString(row.getCell(2));
                        String stream = getCellString(row.getCell(3));

                        if (adm == null || adm.isBlank()) continue;
                        totalRows++;

                        int form;
                        try {
                            form = Integer.parseInt(formStr);
                        } catch (NumberFormatException e) {
                            errors.add("Row " + (r + 1) + ": invalid Form '" + formStr + "'");
                            continue;
                        }
                        if (stream == null || stream.isBlank()) stream = "General";

                        // Check if student exists
                        PreparedStatement checkPs = null;
                        try {
                            checkPs = conn.prepareStatement("SELECT id FROM students WHERE admission_number = ?");
                            checkPs.setString(1, adm.trim());
                            ResultSet rs = checkPs.executeQuery();
                            if (rs.next()) {
                                updatePs.setString(1, name);
                                updatePs.setInt(2, form);
                                updatePs.setString(3, stream);
                                updatePs.setString(4, adm.trim());
                                updatePs.executeUpdate();
                                updated++;
                            } else {
                                insertPs.setString(1, adm.trim());
                                insertPs.setString(2, name);
                                insertPs.setInt(3, form);
                                insertPs.setString(4, stream);
                                insertPs.executeUpdate();
                                inserted++;
                            }
                        } finally {
                            if (checkPs != null) try { checkPs.close(); } catch (SQLException ignored) {}
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw new RuntimeException("Student import failed, transaction rolled back", e);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Student import failed", e);
            }
            return new StudentImportResult(totalRows, inserted, updated, errors.size(), errors);
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new RuntimeException("Failed to read student Excel file", e);
        }
    }

    public void generateTemplate(Path outputPath, long examId, int form, String stream) {
        String examInfo = getExamInfo(examId);
        List<String[]> students = getStudents(form, stream);
        List<SubjectInfo> subjects = getSubjects();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Form " + form + " - " + stream);

            Row header1 = sheet.createRow(0);
            Cell c = header1.createCell(0);
            c.setCellValue("EXAM: " + examInfo);
            c.setCellStyle(boldStyle(wb));

            Row headerRow = sheet.createRow(2);
            String[] cols = {"Admission No.", "Student Name"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(boldStyle(wb));
            }
            for (int i = 0; i < subjects.size(); i++) {
                Cell cell = headerRow.createCell(cols.length + i);
                cell.setCellValue(subjects.get(i).name());
                cell.setCellStyle(boldStyle(wb));
            }

            for (int r = 0; r < students.size(); r++) {
                Row row = sheet.createRow(3 + r);
                row.createCell(0).setCellValue(students.get(r)[0]);
                row.createCell(1).setCellValue(students.get(r)[1]);
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            for (int i = 0; i < subjects.size(); i++) {
                sheet.autoSizeColumn(cols.length + i);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel template", e);
        }
    }

    public ImportResult processUpload(Path inputPath, long examId) {
        List<String> errors = new ArrayList<>();
        int marksInserted = 0;
        int totalRows = 0;

        try (Workbook wb = new XSSFWorkbook(inputPath.toFile())) {
            Sheet sheet = wb.getSheetAt(0);

            Row headerRow = sheet.getRow(2);
            if (headerRow == null) {
                return new ImportResult(0, 0, 1, List.of("Row 3 (header) not found. Ensure the file matches the generated template."));
            }

            Map<Integer, Long> subjectColMap = new HashMap<>();
            Map<String, Long> subjectNameToId = getSubjectNameToIdMap();
            for (int c = 2; c < headerRow.getLastCellNum(); c++) {
                String cellVal = getCellString(headerRow.getCell(c));
                if (cellVal != null && subjectNameToId.containsKey(cellVal)) {
                    subjectColMap.put(c, subjectNameToId.get(cellVal));
                }
            }

            Map<String, Long> admissionToStudentId = getAdmissionToStudentIdMap();

            List<Mark> marksBatch = new ArrayList<>();
            for (int r = 3; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String adm = getCellString(row.getCell(0));
                if (adm == null || adm.isBlank()) continue;
                totalRows++;

                Long studentId;
                try {
                    studentId = resolveStudent(adm, admissionToStudentId, row);
                } catch (Exception e) {
                    errors.add("Row " + (r + 1) + " (" + adm + "): " + e.getMessage());
                    continue;
                }
                if (studentId == null) continue;

                for (var entry : subjectColMap.entrySet()) {
                    int col = entry.getKey();
                    long subjectId = entry.getValue();
                    Cell cell = row.getCell(col);
                    if (cell == null) continue;
                    String scoreStr = getCellString(cell);
                    if (scoreStr == null || scoreStr.isBlank()) continue;
                    try {
                        double score = Double.parseDouble(scoreStr.trim());
                        String gradeResult = analysisService.determineGradeAndPoints(score, subjectId);
                        String[] parts = gradeResult.split("\\|");
                        Mark mark = new Mark(examId, studentId, subjectId, score);
                        mark.setGradeAchieved(parts[0]);
                        mark.setPointsAchieved(Integer.parseInt(parts[1]));
                        marksBatch.add(mark);
                        marksInserted++;
                    } catch (NumberFormatException e) {
                        errors.add("Row " + (r + 1) + ", col " + (col + 1) + ": invalid score '" + scoreStr + "'");
                    }
                }
            }

            marksRepo.batchInsert(marksBatch);
            return new ImportResult(totalRows, marksInserted, errors.size(), errors);
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new RuntimeException("Failed to read uploaded Excel file", e);
        }
    }

    private Long resolveStudent(String adm, Map<String, Long> admissionMap, Row row) {
        Long id = admissionMap.get(adm.trim());
        if (id != null) return id;
        String name = getCellString(row.getCell(1));
        if (name != null && !name.isBlank()) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO students (admission_number, full_name, form, stream) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, adm.trim());
                ps.setString(2, name.trim());
                ps.setInt(3, 1);
                ps.setString(4, "General");
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to auto-register student " + adm, e);
            }
            admissionMap.put(adm.trim(), null);
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id FROM students WHERE admission_number = ?")) {
                ps.setString(1, adm.trim());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    long newId = rs.getLong("id");
                    admissionMap.put(adm.trim(), newId);
                    return newId;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to lookup new student " + adm, e);
            }
        }
        return null;
    }

    private String getExamInfo(long examId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT academic_year, term, exam_series FROM exams WHERE id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("academic_year") + " " + rs.getString("term") + " " + rs.getString("exam_series");
            }
            return "Unknown Exam";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch exam info", e);
        }
    }

    private List<String[]> getStudents(int form, String stream) {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT admission_number, full_name FROM students WHERE form = ? AND stream = ? ORDER BY full_name")) {
            ps.setInt(1, form);
            ps.setString(2, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{rs.getString("admission_number"), rs.getString("full_name")});
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch students", e);
        }
        return list;
    }

    private record SubjectInfo(long id, String name) {}

    private List<SubjectInfo> getSubjects() {
        List<SubjectInfo> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_name FROM subjects ORDER BY subject_name")) {
            while (rs.next()) {
                list.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_name")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch subjects", e);
        }
        return list;
    }

    private Map<String, Long> getSubjectNameToIdMap() {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_name FROM subjects")) {
            while (rs.next()) {
                map.put(rs.getString("subject_name"), rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch subjects", e);
        }
        return map;
    }

    private Map<String, Long> getAdmissionToStudentIdMap() {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number FROM students")) {
            while (rs.next()) {
                map.put(rs.getString("admission_number"), rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch students", e);
        }
        return map;
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
