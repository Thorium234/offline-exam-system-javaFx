package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.service.ExamAnalysisServiceImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class PublishTeacherPanel {

    private final DatabaseEngine db;
    private final IExamAnalysisService analysisService;
    private final String username;
    private long currentExamId;
    private long currentSubjectId;
    private String currentSubjectName;
    private int currentOutOf = 100;

    private final VBox root = new VBox(15);
    private final PublishScoreTable scoreTable;
    private final Label uploadStatus = new Label();
    private final VBox uploadArea = new VBox(10);
    private Runnable onBack;

    public PublishTeacherPanel(DatabaseEngine db, String username) {
        this.db = db;
        this.username = username;
        this.analysisService = new ExamAnalysisServiceImpl();
        this.scoreTable = new PublishScoreTable(db);
    }

    public Node getView() {
        root.setPadding(new Insets(15));
        return root;
    }

    public void showUploadView(long examId, long subjectId, String subjectName, int outOf, Runnable onBack) {
        this.currentExamId = examId;
        this.currentSubjectId = subjectId;
        this.currentSubjectName = subjectName;
        this.currentOutOf = outOf;
        this.onBack = onBack;

        root.getChildren().clear();

        HBox hdr = new HBox(10);
        Label title = new Label("Upload Marks: " + subjectName + " (Out of " + outOf + ")");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        Button backBtn = new Button("Back to Subjects");
        backBtn.setOnAction(e -> { if (onBack != null) onBack.run(); });
        hdr.getChildren().addAll(title, backBtn);

        HBox excelRow = new HBox(10);
        Button templateBtn = new Button("Download Template");
        templateBtn.setOnAction(e -> downloadTemplate());
        Button uploadExcelBtn = new Button("Upload Excel");
        uploadExcelBtn.getStyleClass().addAll("button", "button-primary");
        uploadExcelBtn.setOnAction(e -> uploadExcel());
        excelRow.getChildren().addAll(templateBtn, uploadExcelBtn, new Label("(Excel: column A = Admission, B = Score)"));

        HBox saveRow = new HBox(10);
        Button saveBtn = new Button("Save Marks");
        saveBtn.getStyleClass().addAll("button", "button-success");
        saveBtn.setOnAction(e -> saveUploadedMarks());
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> scoreTable.loadStudents(currentExamId, currentSubjectId, currentOutOf));
        saveRow.getChildren().addAll(saveBtn, refreshBtn, uploadStatus);

        scoreTable.loadStudents(currentExamId, currentSubjectId, currentOutOf);
        uploadStatus.setText(scoreTable.getData().size() + " students");

        root.getChildren().addAll(hdr, excelRow, scoreTable.getTable(), saveRow);
    }

    private void saveUploadedMarks() {
        int saved = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (PublishScoreTable.MarkRow row : scoreTable.getData()) {
                    if (row.score == null || !row.dirty) continue;
                    String result = analysisService.determineGradeAndPoints(row.score, currentSubjectId, currentExamId);
                    String[] parts = result.split("\\|");
                    ps.setLong(1, currentExamId);
                    ps.setLong(2, row.studentId);
                    ps.setLong(3, currentSubjectId);
                    ps.setDouble(4, row.score);
                    ps.setString(5, parts[0]);
                    ps.setInt(6, Integer.parseInt(parts[1]));
                    ps.addBatch();
                    saved++;
                }
                ps.executeBatch();

                try (PreparedStatement up = conn.prepareStatement(
                        "INSERT OR REPLACE INTO exam_subjects (exam_id, subject_id, out_of, uploaded_by, uploaded_at, published, published_by, published_at) " +
                        "VALUES (?,?,?,?,?, COALESCE((SELECT published FROM exam_subjects WHERE exam_id = ? AND subject_id = ?), 0), ?, ?)")) {
                    up.setLong(1, currentExamId);
                    up.setLong(2, currentSubjectId);
                    up.setInt(3, currentOutOf);
                    up.setString(4, username);
                    up.setString(5, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    up.setLong(6, currentExamId);
                    up.setLong(7, currentSubjectId);
                    up.setNull(8, Types.VARCHAR);
                    up.setNull(9, Types.VARCHAR);
                    up.executeUpdate();
                }
                conn.commit();
                uploadStatus.setText("Saved " + saved + " mark(s)");
                scoreTable.loadStudents(currentExamId, currentSubjectId, currentOutOf);
            } catch (SQLException e) {
                conn.rollback();
                UIUtils.showError("Failed to save: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            UIUtils.showError("DB error: " + e.getMessage());
        }
    }

    private void downloadTemplate() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Template");
        fc.setInitialFileName("marks_template_" + currentSubjectName.replace(" ", "_") + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Marks");
            Row hdr = sheet.createRow(0);
            hdr.createCell(0).setCellValue("Admission No.");
            hdr.createCell(1).setCellValue("Score (Out of " + currentOutOf + ")");
            sheet.autoSizeColumn(0); sheet.autoSizeColumn(1);
            try (FileOutputStream fos = new FileOutputStream(f)) { wb.write(fos); }
            UIUtils.showError("Template saved.");
        } catch (Exception e) {
            UIUtils.showError("Failed to save template: " + e.getMessage());
        }
    }

    private void uploadExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Marks Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        if (f.length() > 10_485_760) { UIUtils.showError("File too large."); return; }

        List<String> errors = new ArrayList<>();
        int imported = 0, totalRows = 0;

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
            Sheet sheet = wb.getSheetAt(0);
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String adm = getCellString(row.getCell(0));
                    if (adm == null || adm.isBlank()) continue;
                    totalRows++;
                    double score;
                    try { score = Double.parseDouble(getCellString(row.getCell(1))); }
                    catch (Exception e) { errors.add("Row " + (r + 1) + ": invalid score"); continue; }
                    if (!Double.isFinite(score) || score < 0 || score > currentOutOf) {
                        errors.add("Row " + (r + 1) + ": score must be between 0 and " + currentOutOf);
                        continue;
                    }
                    long studentId;
                    try (PreparedStatement sp = conn.prepareStatement("SELECT id FROM students WHERE admission_number = ? AND deallocated = 0")) {
                        sp.setString(1, adm.trim());
                        ResultSet sr = sp.executeQuery();
                        if (!sr.next()) { errors.add("Row " + (r + 1) + ": student " + adm + " not found"); continue; }
                        studentId = sr.getLong("id");
                    }
                    String gradeResult = analysisService.determineGradeAndPoints(score, currentSubjectId, currentExamId);
                    String[] parts = gradeResult.split("\\|");
                    ps.setLong(1, currentExamId);
                    ps.setLong(2, studentId);
                    ps.setLong(3, currentSubjectId);
                    ps.setDouble(4, score);
                    ps.setString(5, parts[0]);
                    ps.setInt(6, Integer.parseInt(parts[1]));
                    ps.addBatch();
                    imported++;
                    if (imported % 100 == 0) ps.executeBatch();
                }
                ps.executeBatch();
                try (PreparedStatement up = conn.prepareStatement(
                        "INSERT OR REPLACE INTO exam_subjects (exam_id, subject_id, out_of, uploaded_by, uploaded_at, published, published_by, published_at) " +
                        "VALUES (?,?,?,?,?, COALESCE((SELECT published FROM exam_subjects WHERE exam_id = ? AND subject_id = ?), 0), ?, ?)")) {
                    up.setLong(1, currentExamId);
                    up.setLong(2, currentSubjectId);
                    up.setInt(3, currentOutOf);
                    up.setString(4, username);
                    up.setString(5, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    up.setLong(6, currentExamId);
                    up.setLong(7, currentSubjectId);
                    up.setNull(8, Types.VARCHAR);
                    up.setNull(9, Types.VARCHAR);
                    up.executeUpdate();
                }
                String msg = "Imported " + imported + " marks from " + totalRows + " rows";
                if (!errors.isEmpty()) msg += " (" + errors.size() + " errors)";
                uploadStatus.setText(msg);
                scoreTable.loadStudents(currentExamId, currentSubjectId, currentOutOf);
                if (!errors.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Errors:\n");
                    errors.stream().limit(10).forEach(e -> sb.append(e).append("\n"));
                    UIUtils.showError(sb.toString());
                }
            }
        } catch (Exception e) {
            UIUtils.showError("Excel import failed: " + e.getMessage());
        }
    }

    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            default -> null;
        };
    }
}
