package com.zaraki.exams.service;

import com.zaraki.exams.database.DatabaseEngine;
import static com.zaraki.exams.database.DatabaseEngine.validateFilterColumn;
import com.zaraki.exams.model.Mark;
import com.zaraki.exams.repository.IMarksRepository;
import com.zaraki.exams.repository.MarksRepositoryImpl;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelServiceImpl implements IExcelService {

    private final DatabaseEngine db;
    private final IMarksRepository marksRepo;
    private final IExamAnalysisService analysisService;

    public ExcelServiceImpl() {
        this.db = DatabaseEngine.getInstance();
        this.marksRepo = new MarksRepositoryImpl();
        this.analysisService = new ExamAnalysisServiceImpl();
    }

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

    public void generateStudentListExcel(Path outputPath, String filterCol, String filterValue) {
        filterCol = validateFilterColumn(filterCol);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Student List");
            Row headerRow = sheet.createRow(0);
            String[] cols = {"#", "Admission No.", "Full Name", "Form", "Stream"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(boldStyle(wb));
            }

            String where = filterCol.isEmpty() ? "" : " WHERE " + filterCol + " = ?";
            String sql = "SELECT admission_number, full_name, form, stream FROM students" + where + " ORDER BY form, stream, admission_number";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!filterCol.isEmpty()) ps.setString(1, filterValue);
                ResultSet rs = ps.executeQuery();
                int num = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(num);
                    row.createCell(0).setCellValue(num++);
                    row.createCell(1).setCellValue(rs.getString("admission_number"));
                    row.createCell(2).setCellValue(rs.getString("full_name"));
                    row.createCell(3).setCellValue("Form " + rs.getInt("form"));
                    row.createCell(4).setCellValue(rs.getString("stream"));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) { wb.write(fos); }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate student list Excel", e);
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
                        try (PreparedStatement checkPs = conn.prepareStatement("SELECT id FROM students WHERE admission_number = ?")) {
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
        List<SubjectInfo> subjects = getSubjectsForExamAndStream(examId, form, stream);

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
                Cell scoreCell = headerRow.createCell(cols.length + i);
                scoreCell.setCellValue(subjects.get(i).name());
                scoreCell.setCellStyle(boldStyle(wb));
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

    public void generateSubjectTemplate(Path outputPath, long examId, int form, String stream, long subjectId) {
        String subjectName = getSubjectName(subjectId);
        String examInfo = getExamInfo(examId);
        List<String[]> students = getStudents(form, stream);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(subjectName);

            Row header0 = sheet.createRow(0);
            Cell c0 = header0.createCell(0);
            c0.setCellValue("EXAM: " + examInfo);
            c0.setCellStyle(boldStyle(wb));

            Row header1 = sheet.createRow(1);
            Cell c1 = header1.createCell(0);
            c1.setCellValue("SUBJECT: " + subjectName);
            c1.setCellStyle(boldStyle(wb));

            Row headerRow = sheet.createRow(3);
            String[] cols = {"Admission No.", "Student Name", subjectName + " Marks"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(boldStyle(wb));
            }

            for (int r = 0; r < students.size(); r++) {
                Row row = sheet.createRow(4 + r);
                row.createCell(0).setCellValue(students.get(r)[0]);
                row.createCell(1).setCellValue(students.get(r)[1]);
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate subject Excel template", e);
        }
    }

    public ImportResult processSubjectUpload(Path inputPath, long examId, long subjectId, int form, String stream) {
        List<String> errors = new ArrayList<>();
        int marksInserted = 0;
        int totalRows = 0;

        int maxScore;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            maxScore = rs.next() ? rs.getInt(1) : 100;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load subject out_of", e);
        }

        Map<String, Long> admissionToStudentId = getAdmissionToStudentIdMap();
        List<Mark> marksBatch = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(inputPath.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String adm = getCellString(row.getCell(0));
                if (adm == null || adm.isBlank()) continue;
                totalRows++;

                Long studentId;
                try {
                    studentId = resolveStudent(adm, admissionToStudentId, row, form, stream);
                } catch (Exception e) {
                    errors.add("Row " + (r + 1) + " (" + adm + "): " + e.getMessage());
                    continue;
                }
                if (studentId == null) continue;

                Cell scoreCell = row.getCell(2);
                if (scoreCell == null) continue;
                String scoreStr = getCellString(scoreCell);
                if (scoreStr == null || scoreStr.isBlank()) continue;
                try {
                    double score = Double.parseDouble(scoreStr.trim());
                    if (!Double.isFinite(score) || score < 0) {
                        errors.add("Row " + (r + 1) + ": invalid score '" + scoreStr + "'");
                        continue;
                    }
                    if (score > maxScore) {
                        errors.add("Row " + (r + 1) + ": score " + score + " exceeds maximum " + maxScore);
                        continue;
                    }
                    String gradeResult = analysisService.determineGradeAndPoints(score, subjectId, examId);
                    String[] parts = gradeResult.split("\\|");
                    Mark mark = new Mark(examId, studentId, subjectId, score);
                    mark.setGradeAchieved(parts[0]);
                    mark.setPointsAchieved(Integer.parseInt(parts[1]));
                    marksBatch.add(mark);
                    marksInserted++;
                } catch (NumberFormatException e) {
                    errors.add("Row " + (r + 1) + ": invalid score '" + scoreStr + "'");
                }
            }

            marksRepo.batchInsert(marksBatch);
            return new ImportResult(totalRows, marksInserted, errors.size(), errors);
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new RuntimeException("Failed to read uploaded Excel file", e);
        }
    }

    public void generateTeacherMultiSheetTemplate(Path outputPath, long examId, long userId) {
        String examInfo = getExamInfo(examId);

        List<TeacherAssignment> assignments = new ArrayList<>();
        String sql = """
            SELECT ts.subject_id, s.subject_name, s.subject_code, ts.form, ts.stream
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name, ts.form, ts.stream
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                assignments.add(new TeacherAssignment(
                    rs.getLong("subject_id"),
                    rs.getString("subject_code"),
                    rs.getString("subject_name"),
                    rs.getInt("form"),
                    rs.getString("stream")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load teacher assignments", e);
        }

        if (assignments.isEmpty()) {
            throw new RuntimeException("No subject assignments found for this teacher");
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Set<String> usedNames = new HashSet<>();
            for (TeacherAssignment ta : assignments) {
                String baseName = "S" + ta.subjectId() + "_" + ta.subjectName() + "_F" + ta.form() + "_" + ta.stream();
                String sheetName = baseName.length() > 31 ? baseName.substring(0, 31) : baseName;
                String uniqueName = sheetName;
                int counter = 1;
                while (usedNames.contains(uniqueName)) {
                    String suffix = "_" + (counter++);
                    uniqueName = (sheetName.length() > 31 - suffix.length() ? sheetName.substring(0, 31 - suffix.length()) : sheetName) + suffix;
                }
                usedNames.add(uniqueName);

                Sheet sheet = wb.createSheet(uniqueName);

                Row header0 = sheet.createRow(0);
                Cell c0 = header0.createCell(0);
                c0.setCellValue("EXAM: " + examInfo);
                c0.setCellStyle(boldStyle(wb));

                Row header1 = sheet.createRow(1);
                Cell c1 = header1.createCell(0);
                c1.setCellValue("SUBJECT: " + ta.subjectName() + " | Form " + ta.form() + " - " + ta.stream());
                c1.setCellStyle(boldStyle(wb));

                Row headerRow = sheet.createRow(3);
                String[] cols = {"Admission No.", "Student Name", ta.subjectName() + " Marks"};
                for (int i = 0; i < cols.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(cols[i]);
                    cell.setCellStyle(boldStyle(wb));
                }

                List<String[]> students = getStudents(ta.form(), ta.stream());
                for (int r = 0; r < students.size(); r++) {
                    Row row = sheet.createRow(4 + r);
                    row.createCell(0).setCellValue(students.get(r)[0]);
                    row.createCell(1).setCellValue(students.get(r)[1]);
                }

                sheet.autoSizeColumn(0);
                sheet.autoSizeColumn(1);
                sheet.autoSizeColumn(2);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate teacher multi-sheet template", e);
        }
    }

    public ImportResult processTeacherMultiSheetUpload(Path inputPath, long examId, int form, String stream) {
        List<String> errors = new ArrayList<>();
        int marksInserted = 0;
        int totalRows = 0;

        Map<String, Long> admissionToStudentId = getAdmissionToStudentIdMap();

        try (Workbook wb = new XSSFWorkbook(inputPath.toFile())) {
            List<Mark> allMarks = new ArrayList<>();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                long subjectId;
                try {
                    subjectId = Long.parseLong(sheetName.split("_")[0].substring(1));
                } catch (Exception e) {
                    errors.add("Sheet '" + sheetName + "': could not parse subject ID from sheet name");
                    continue;
                }

                int maxScore;
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
                    ps.setLong(1, examId);
                    ps.setLong(2, subjectId);
                    ResultSet rs = ps.executeQuery();
                    maxScore = rs.next() ? rs.getInt(1) : 100;
                } catch (SQLException e) {
                    errors.add("Sheet '" + sheetName + "': could not load max score for subject " + subjectId);
                    continue;
                }

                for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String adm = getCellString(row.getCell(0));
                    if (adm == null || adm.isBlank()) continue;
                    totalRows++;

                    Long studentId;
                    try {
                    studentId = resolveStudent(adm, admissionToStudentId, row, form, stream);
                    } catch (Exception e) {
                        errors.add("Sheet '" + sheetName + "', Row " + (r + 1) + " (" + adm + "): " + e.getMessage());
                        continue;
                    }
                    if (studentId == null) continue;

                    Cell scoreCell = row.getCell(2);
                    if (scoreCell == null) continue;
                    String scoreStr = getCellString(scoreCell);
                    if (scoreStr == null || scoreStr.isBlank()) continue;
                    try {
                        double score = Double.parseDouble(scoreStr.trim());
                        if (!Double.isFinite(score) || score < 0) {
                            errors.add("Sheet '" + sheetName + "', Row " + (r + 1) + ": invalid score '" + scoreStr + "'");
                            continue;
                        }
                        if (score > maxScore) {
                            errors.add("Sheet '" + sheetName + "', Row " + (r + 1) + ": score " + score + " exceeds maximum " + maxScore);
                            continue;
                        }
                        String gradeResult = analysisService.determineGradeAndPoints(score, subjectId, examId);
                        String[] parts = gradeResult.split("\\|");
                        Mark mark = new Mark(examId, studentId, subjectId, score);
                        mark.setGradeAchieved(parts[0]);
                        mark.setPointsAchieved(Integer.parseInt(parts[1]));
                        allMarks.add(mark);
                        marksInserted++;
                    } catch (NumberFormatException e) {
                        errors.add("Sheet '" + sheetName + "', Row " + (r + 1) + ": invalid score '" + scoreStr + "'");
                    }
                }
            }

            marksRepo.batchInsert(allMarks);
            return new ImportResult(totalRows, marksInserted, errors.size(), errors);
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new RuntimeException("Failed to read uploaded multi-sheet Excel file", e);
        }
    }

    private record TeacherAssignment(long subjectId, String subjectCode, String subjectName, int form, String stream) {}

    private List<SubjectInfo> getSubjectsForExamAndStream(long examId, int form, String stream) {
        List<SubjectInfo> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT sub.id, sub.subject_name
            FROM subjects sub
            WHERE sub.id IN (
                SELECT subject_id FROM exam_subjects WHERE exam_id = ?
                INTERSECT
                SELECT subject_id FROM stream_subjects WHERE form = ? AND stream = ?
            )
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setInt(2, form);
            ps.setString(3, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_name")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch subjects", e);
        }
        // Fallback: if no subjects match, return all subjects in the exam
        if (list.isEmpty()) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT DISTINCT sub.id, sub.subject_name FROM subjects sub JOIN exam_subjects es ON es.subject_id = sub.id WHERE es.exam_id = ? ORDER BY sub.subject_name")) {
                ps.setLong(1, examId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_name")));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch subjects", e);
            }
        }
        return list;
    }

    public ImportResult processUpload(Path inputPath, long examId, int form, String stream) {
        List<String> errors = new ArrayList<>();
        int marksInserted = 0;
        int totalRows = 0;

        // Pre-query out_of for all subjects for this exam
        Map<Long, Integer> subjectOutOf = new HashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT subject_id, COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ?")) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) subjectOutOf.put(rs.getLong(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load subject out_of values", e);
        }

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
                    studentId = resolveStudent(adm, admissionToStudentId, row, form, stream);
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
                        if (!Double.isFinite(score) || score < 0) {
                            errors.add("Row " + (r + 1) + ", col " + (col + 1) + ": invalid score '" + scoreStr + "'");
                            continue;
                        }
                        int maxScore = subjectOutOf.getOrDefault(subjectId, 100);
                        if (score > maxScore) {
                            errors.add("Row " + (r + 1) + ", col " + (col + 1) + ": score " + score + " exceeds maximum " + maxScore);
                            continue;
                        }
                        String gradeResult = analysisService.determineGradeAndPoints(score, subjectId, examId);
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

    private Long resolveStudent(String adm, Map<String, Long> admissionMap, Row row, int form, String stream) {
        Long id = admissionMap.get(adm.trim());
        if (id != null) return id;
        String name = getCellString(row.getCell(1));
        if (name != null && !name.isBlank()) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO students (admission_number, full_name, form, stream) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, adm.trim());
                ps.setString(2, name.trim());
                ps.setInt(3, form);
                ps.setString(4, stream);
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

    private String getSubjectName(long subjectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT subject_name FROM subjects WHERE id = ?")) {
            ps.setLong(1, subjectId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("subject_name") : "Subject";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch subject name", e);
        }
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
                 "SELECT admission_number, full_name FROM students WHERE form = ? AND stream = ? AND deallocated = 0 ORDER BY full_name")) {
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
