package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.ExamRepository;
import com.zaraki.exams.repository.UserRepository;
import com.zaraki.exams.service.ExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.util.converter.DoubleStringConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PublishForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;
    private final ExamRepository examRepo;
    private final UserRepository userRepo;
    private final String displayName;
    private final String username;
    private final String role;
    private long currentUserId;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final VBox subjectTableArea = new VBox(10);
    private final VBox uploadArea = new VBox(10);
    private final Label statusLabel = new Label();
    private final TableView<SubjectRow> subjectTable = new TableView<>();
    private final ObservableList<SubjectRow> subjectData = FXCollections.observableArrayList();

    // Upload state
    private long currentExamId;
    private long currentSubjectId;
    private String currentSubjectName;
    private VBox uploadView;
    private TableView<MarkRow> uploadTable;
    private final Label uploadStatus = new Label();
    private int currentOutOf = 100;

    public PublishForm(DatabaseEngine db, String displayName, String username, String role) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
        this.examRepo = new ExamRepository();
        this.userRepo = new UserRepository();
        this.displayName = displayName;
        this.username = username;
        this.role = role;
        this.currentUserId = userRepo.resolveUserId(username);
    }

    public VBox getView() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("Publish Management");

        Label info = new Label("Upload marks per subject, publish each subject, then release the exam for report generation.");

        HBox examRow = new HBox(10);
        examRow.getChildren().addAll(new Label("Exam:"), examBox);
        Button loadBtn = new Button("Load Subjects");
        loadBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white;");
        examRow.getChildren().add(loadBtn);

        subjectTableArea.setVisible(false);
        buildSubjectTable();

        uploadArea.setVisible(false);

        root.getChildren().addAll(header, info, examRow, statusLabel, subjectTableArea, uploadArea);

        loadExams();
        loadBtn.setOnAction(e -> loadSubjectStatus());

        return root;
    }

    private void buildSubjectTable() {
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.setItems(subjectData);
        subjectTable.setPrefHeight(300);

        TableColumn<SubjectRow, Integer> colNum = new TableColumn<>("#");
        colNum.setCellValueFactory(new PropertyValueFactory<>("num"));
        colNum.setPrefWidth(40);

        TableColumn<SubjectRow, String> colSubj = new TableColumn<>("Subject");
        colSubj.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
        colSubj.setPrefWidth(180);

        TableColumn<SubjectRow, Integer> colOutOf = new TableColumn<>("Out Of");
        colOutOf.setCellValueFactory(new PropertyValueFactory<>("outOf"));
        colOutOf.setCellFactory(TextFieldTableCell.forTableColumn(new javafx.util.converter.IntegerStringConverter()));
        colOutOf.setOnEditCommit(e -> {
            SubjectRow row = e.getRowValue();
            int v = Math.max(1, e.getNewValue());
            row.setOutOf(v);
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE exam_subjects SET out_of = ? WHERE exam_id = ? AND subject_id = ?")) {
                ps.setInt(1, v);
                ps.setLong(2, currentExamId);
                ps.setLong(3, row.subjectId);
                ps.executeUpdate();
            } catch (SQLException ex) { UIUtils.showError("Failed to update out_of: " + ex.getMessage()); }
        });
        colOutOf.setPrefWidth(70);

        TableColumn<SubjectRow, String> colMarks = new TableColumn<>("Marks Status");
        colMarks.setCellValueFactory(new PropertyValueFactory<>("marksStatus"));
        colMarks.setPrefWidth(120);

        TableColumn<SubjectRow, String> colPub = new TableColumn<>("Published");
        colPub.setCellValueFactory(new PropertyValueFactory<>("publishedStatus"));
        colPub.setPrefWidth(120);

        TableColumn<SubjectRow, Void> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(180);
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button uploadBtn = new Button("Upload Marks");
            private final Button publishBtn = new Button("Publish");
            { 
                uploadBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-size: 11;");
                publishBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-size: 11;");
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                SubjectRow row = (SubjectRow) getTableRow().getItem();
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER);
                if (!row.published) {
                    Button up = row.marksCount > 0 ? new Button("Re-upload") : new Button("Upload Marks");
                    up.setStyle(uploadBtn.getStyle());
                    up.setOnAction(e -> showUploadView(row));
                    box.getChildren().add(up);
                    if (row.marksCount > 0) {
                        Button pub = new Button("Publish");
                        pub.setStyle(publishBtn.getStyle());
                        pub.setOnAction(e -> publishSubject(row));
                        box.getChildren().add(pub);
                    }
                } else {
                    Label done = new Label("Done");
                    done.setTextFill(Color.GREEN);
                    done.setFont(Font.font("System", FontWeight.BOLD, 12));
                    Label by = new Label("by " + row.publishedBy);
                    by.setFont(Font.font("System", 10));
                    by.setTextFill(Color.gray(0.5));
                    box.getChildren().addAll(done, by);
                }
                setGraphic(box);
            }
        });

        subjectTable.getColumns().addAll(colNum, colSubj, colOutOf, colMarks, colPub, colAction);
        subjectTable.setEditable(true);

        // Action buttons
        HBox actionRow = new HBox(10);
        Button releaseBtn = new Button("Release Exam (Admin Only)");
        releaseBtn.setStyle("-fx-background-color: #e65100; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        releaseBtn.setPrefWidth(300);
        releaseBtn.setOnAction(e -> releaseExam());
        actionRow.getChildren().add(releaseBtn);

        if (role.equals("admin")) {
            Button multiUploadBtn = new Button("Upload Multi-Sheet Excel (Admin)");
            multiUploadBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");
            multiUploadBtn.setOnAction(e -> uploadMultiSheetExcel());
            actionRow.getChildren().add(multiUploadBtn);

            Button teacherTemplateBtn = new Button("Generate Teacher Template");
            teacherTemplateBtn.setOnAction(e -> generateTeacherTemplate());
            actionRow.getChildren().add(teacherTemplateBtn);
        }

        VBox tableWrapper = new VBox(10, subjectTable, actionRow);
        subjectTableArea.getChildren().add(tableWrapper);
    }

    private void loadExams() {
        try {
            var exams = examRepo.findAllDesc();
            for (var e : exams)
                examBox.getItems().add(e.get("id") + " - " + e.get("academic_year")
                    + " " + e.get("term") + " " + e.get("exam_series"));
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void loadSubjectStatus() {
        if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
        currentExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        uploadArea.setVisible(false);
        subjectData.clear();

        // Ensure exam_subjects rows exist for all subjects
        String ensureSql = "INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) SELECT ?, id, 100 FROM subjects";
        try (PreparedStatement ps = db.getConnection().prepareStatement(ensureSql)) {
            ps.setLong(1, currentExamId);
            ps.executeUpdate();
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); return; }

        StringBuilder sql = new StringBuilder("""
            SELECT s.id, s.subject_name, s.subject_code,
                   COALESCE(es.out_of, 100) AS out_of,
                   COALESCE(es.published, 0) AS published,
                   es.published_by, es.uploaded_by, es.uploaded_at,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = s.id) AS marks_count
            FROM subjects s
            LEFT JOIN exam_subjects es ON es.exam_id = ? AND es.subject_id = s.id
            """);

        // For teachers, filter to only their assigned subjects
        if (!role.equals("admin") && currentUserId > 0) {
            sql.append(" JOIN teacher_subjects ts ON ts.subject_id = s.id AND ts.user_id = ?");
        }

        sql.append(" ORDER BY s.subject_name");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setLong(idx++, currentExamId);
            ps.setLong(idx++, currentExamId);
            if (!role.equals("admin") && currentUserId > 0) ps.setLong(idx++, currentUserId);
            ResultSet rs = ps.executeQuery();
            int num = 0;
            while (rs.next()) {
                num++;
                int marksCount = rs.getInt("marks_count");
                boolean published = rs.getInt("published") == 1;
                subjectData.add(new SubjectRow(
                    num, rs.getLong("id"), rs.getString("subject_name"),
                    rs.getInt("out_of"), marksCount,
                    marksCount > 0 ? (published ? "Published" : "Uploaded") : "Not Uploaded",
                    published ? "Yes" : "No",
                    published, rs.getString("published_by")
                ));
            }
            String released = isExamReleased(currentExamId) ? " (RELEASED)" : "";
            statusLabel.setText("Exam: " + examBox.getValue() + released + " | " + num + " subjects");
            subjectTableArea.setVisible(true);
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
    }

    // ─── Upload View ─────────────────────────────────────────

    private void showUploadView(SubjectRow row) {
        currentSubjectId = row.subjectId;
        currentSubjectName = row.subjectName;
        currentOutOf = row.outOf;
        subjectTableArea.setVisible(false);
        uploadArea.getChildren().clear();
        uploadArea.setVisible(true);

        // Header
        HBox hdr = new HBox(10);
        Label title = new Label("Upload Marks: " + currentSubjectName + " (Out of " + currentOutOf + ")");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        Button backBtn = new Button("Back to Subjects");
        backBtn.setOnAction(e -> { uploadArea.setVisible(false); subjectTableArea.setVisible(true); });
        hdr.getChildren().addAll(title, backBtn);

        // Excel upload
        HBox excelRow = new HBox(10);
        Button templateBtn = new Button("Download Template");
        templateBtn.setOnAction(e -> downloadTemplate());
        Button uploadExcelBtn = new Button("Upload Excel");
        uploadExcelBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white;");
        uploadExcelBtn.setOnAction(e -> uploadExcel());
        excelRow.getChildren().addAll(templateBtn, uploadExcelBtn, new Label("(Excel: column A = Admission, B = Score)"));

        // Student table
        uploadTable = new TableView<>();
        uploadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        uploadTable.setEditable(true);
        uploadTable.setPrefHeight(350);

        TableColumn<MarkRow, Integer> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("pos")); cPos.setPrefWidth(40);
        TableColumn<MarkRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admission")); cAdm.setPrefWidth(120);
        TableColumn<MarkRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("name")); cName.setPrefWidth(200);
        TableColumn<MarkRow, Double> cScore = new TableColumn<>("Score / " + currentOutOf);
        cScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        cScore.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        cScore.setOnEditCommit(e -> {
            MarkRow mr = e.getRowValue();
            Double v = e.getNewValue();
            if (v != null && Double.isFinite(v) && v >= 0 && v <= currentOutOf) {
                mr.score = v;
                mr.dirty = true;
                uploadTable.refresh();
            } else if (v != null && !Double.isFinite(v)) {
                UIUtils.showError("Invalid score value.");
            } else if (v != null) {
                UIUtils.showError("Score must be between 0 and " + currentOutOf + ".");
            }
        });
        cScore.setPrefWidth(100);
        TableColumn<MarkRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("grade")); cGrade.setPrefWidth(60);
        TableColumn<MarkRow, Integer> cPts = new TableColumn<>("Points");
        cPts.setCellValueFactory(new PropertyValueFactory<>("points")); cPts.setPrefWidth(60);

        uploadTable.getColumns().addAll(cPos, cAdm, cName, cScore, cGrade, cPts);

        HBox saveRow = new HBox(10);
        Button saveBtn = new Button("Save Marks");
        saveBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveUploadedMarks());
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadUploadStudents());
        saveRow.getChildren().addAll(saveBtn, refreshBtn, uploadStatus);

        loadUploadStudents();

        uploadArea.getChildren().addAll(hdr, excelRow, uploadTable, saveRow);
    }

    private void loadUploadStudents() {
        ObservableList<MarkRow> rows = FXCollections.observableArrayList();
        String sql = """
            SELECT s.id, s.admission_number, s.full_name, m.score, m.grade_achieved, m.points_achieved
            FROM students s
            LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? AND m.subject_id = ?
            WHERE s.deallocated = 0
            ORDER BY s.admission_number
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, currentExamId);
            ps.setLong(2, currentSubjectId);
            ResultSet rs = ps.executeQuery();
            int pos = 0;
            while (rs.next()) {
                pos++;
                Double score = rs.getObject("score") != null ? rs.getDouble("score") : null;
                rows.add(new MarkRow(pos, rs.getLong("id"), rs.getString("admission_number"),
                    rs.getString("full_name"), score, rs.getString("grade_achieved"),
                    rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null, false));
            }
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
        uploadTable.setItems(rows);
        uploadStatus.setText(rows.size() + " students");
    }

    private void saveUploadedMarks() {
        int saved = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (MarkRow row : uploadTable.getItems()) {
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

                // Update exam_subjects uploaded status
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
                loadUploadStudents();
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
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
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
        if (f.length() > 10_485_760) { UIUtils.showError("File too large. Maximum size is 10 MB."); return; }

        List<String> errors = new ArrayList<>();
        int imported = 0;
        int totalRows = 0;

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

                    // Resolve student
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

                // Update exam_subjects uploaded status
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
                loadUploadStudents();
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

    // ─── Publish / Release ──────────────────────────────────

    private void publishSubject(SubjectRow row) {
        if (!role.equals("teacher") && !role.equals("admin")) {
            UIUtils.showError("Only teachers and admins can publish subjects.");
            return;
        }
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE exam_subjects SET published = 1, published_by = ?, published_at = ? WHERE exam_id = ? AND subject_id = ?")) {
            ps.setString(1, username);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            ps.setLong(3, currentExamId);
            ps.setLong(4, row.subjectId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                UIUtils.showError("Subject \"" + row.subjectName + "\" published.");
                loadSubjectStatus();
            }
        } catch (SQLException e) {
            UIUtils.showError("Failed to publish: " + e.getMessage());
        }
    }

    private void releaseExam() {
        if (!role.equals("admin")) {
            UIUtils.showError("Only administrators can release an exam.");
            return;
        }
        // Check all subjects published
        String checkSql = "SELECT COUNT(*) FROM exam_subjects WHERE exam_id = ? AND published = 0";
        try (PreparedStatement ps = db.getConnection().prepareStatement(checkSql)) {
            ps.setLong(1, currentExamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                UIUtils.showError("All subjects must be published before releasing the exam. " + rs.getInt(1) + " subject(s) not yet published.");
                return;
            }
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); return; }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE exams SET released = 1, released_by = ?, released_at = ? WHERE id = ?")) {
            ps.setString(1, username);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            ps.setLong(3, currentExamId);
            ps.executeUpdate();
            UIUtils.showError("Exam released. Reports can now be generated.");
            loadSubjectStatus();
        } catch (SQLException e) {
            UIUtils.showError("Failed to release: " + e.getMessage());
        }
    }

    public static boolean isExamReleased(long examId) {
        return new ExamRepository().isReleased(examId);
    }

    // ─── Multi-Sheet Excel Upload ──────────────────────────

    private void uploadMultiSheetExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Multi-Sheet Marks Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        if (f.length() > 10_485_760) { UIUtils.showError("File too large. Maximum size is 10 MB."); return; }

        long examId = currentExamId;
        List<String> errors = new ArrayList<>();
        int totalImported = 0;
        int totalRows = 0;

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = wb.getSheetName(s);
                if (sheetName == null || sheetName.isBlank()) continue;

                // Parse sheet name: "SubjectName - FormXStream" or just "SubjectName"
                String subjectName;
                Integer form = null;
                String stream = null;

                if (sheetName.contains("-")) {
                    String[] parts = sheetName.split("-", 2);
                    subjectName = parts[0].trim();
                    String classPart = parts[1].trim().toUpperCase();
                    // Parse "FORMXSTREAM" or "XSTREAM" or "X STREAM"
                    classPart = classPart.replace("FORM ", "").replace("FORM", "").trim();
                    StringBuilder formSb = new StringBuilder();
                    int ci = 0;
                    while (ci < classPart.length() && Character.isDigit(classPart.charAt(ci))) {
                        formSb.append(classPart.charAt(ci));
                        ci++;
                    }
                    if (formSb.length() > 0) {
                        form = Integer.parseInt(formSb.toString());
                        stream = classPart.substring(ci).trim();
                    }
                } else {
                    subjectName = sheetName.trim();
                }

                if (subjectName.isEmpty()) { errors.add("Sheet '" + sheetName + "': could not parse subject name"); continue; }

                // Resolve subject_id
                long subjectId;
                try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT id FROM subjects WHERE subject_name = ?")) {
                    ps.setString(1, subjectName);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) { errors.add("Sheet '" + sheetName + "': unknown subject '" + subjectName + "'"); continue; }
                    subjectId = rs.getLong("id");
                }

                // Resolve students - if form/stream specified, only import matching students
                Map<String, Long> studentMap = new HashMap<>();
                String studentSql;
                if (form != null && stream != null && !stream.isEmpty()) {
                    studentSql = "SELECT id, admission_number FROM students WHERE form = ? AND stream = ? AND deallocated = 0";
                } else {
                    studentSql = "SELECT id, admission_number FROM students WHERE deallocated = 0";
                }
                try (PreparedStatement ps = db.getConnection().prepareStatement(studentSql)) {
                    if (form != null && stream != null && !stream.isEmpty()) {
                        ps.setInt(1, form);
                        ps.setString(2, stream);
                    }
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) studentMap.put(rs.getString("admission_number"), rs.getLong("id"));
                }

                // Ensure exam_subjects row
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) VALUES (?,?,100)")) {
                    ps.setLong(1, examId);
                    ps.setLong(2, subjectId);
                    ps.executeUpdate();
                }

                // Get out_of for this subject
                int subjectOutOf = 100;
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "SELECT COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
                    ps.setLong(1, examId);
                    ps.setLong(2, subjectId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) subjectOutOf = rs.getInt(1);
                }

                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {

                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        String adm = getCellString(row.getCell(0));
                        if (adm == null || adm.isBlank()) continue;
                        totalRows++;

                        Long studentId = studentMap.get(adm.trim());
                        if (studentId == null) {
                            errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": student " + adm + " not found");
                            continue;
                        }

                        String scoreStr = getCellString(row.getCell(2));
                        if (scoreStr == null || scoreStr.isBlank()) continue;
                        double score;
                        try { score = Double.parseDouble(scoreStr.trim()); }
                        catch (NumberFormatException e) { errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": invalid score"); continue; }
                        if (!Double.isFinite(score) || score < 0 || score > subjectOutOf) {
                            errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": score must be between 0 and " + subjectOutOf);
                            continue;
                        }

                        String gradeResult = analysisService.determineGradeAndPoints(score, subjectId, examId);
                        String[] parts = gradeResult.split("\\|");

                        ps.setLong(1, examId);
                        ps.setLong(2, studentId);
                        ps.setLong(3, subjectId);
                        ps.setDouble(4, score);
                        ps.setString(5, parts[0]);
                        ps.setInt(6, Integer.parseInt(parts[1]));
                        ps.addBatch();
                        totalImported++;
                        if (totalImported % 200 == 0) ps.executeBatch();
                    }
                    ps.executeBatch();
                }
            }

            String msg = "Imported " + totalImported + " marks from " + totalRows + " rows across " + wb.getNumberOfSheets() + " sheets";
            if (!errors.isEmpty()) msg += " (" + errors.size() + " errors)";
            UIUtils.showError(msg);
            loadSubjectStatus();
        } catch (Exception e) {
            UIUtils.showError("Multi-sheet upload failed: " + e.getMessage());
        }
    }

    // ─── Teacher Template Generation ────────────────────────

    private void generateTeacherTemplate() {
        // Choose a teacher
        ComboBox<String> teacherSelector = new ComboBox<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, full_name FROM users WHERE role = 'teacher' ORDER BY full_name")) {
            while (rs.next())
                teacherSelector.getItems().add(rs.getLong("id") + ":" + rs.getString("username") + " | " + rs.getString("full_name"));
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); return; }

        if (teacherSelector.getItems().isEmpty()) { UIUtils.showError("No teachers found."); return; }
        teacherSelector.setPrefWidth(300);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select Teacher");
        dialog.getDialogPane().setContent(new VBox(10, new Label("Select teacher to generate template for:"), teacherSelector));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? teacherSelector.getValue() : null);
        String result = dialog.showAndWait().orElse(null);
        if (result == null) return;

        long teacherUserId = Long.parseLong(result.split(":")[0]);
        String teacherLabel = result.split("\\|")[1].trim();

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Teacher Template");
        fc.setInitialFileName("marks_template_" + teacherLabel.replace(" ", "_") + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File f = fc.showSaveDialog(null);
        if (f == null) return;

        try (Workbook wb = new XSSFWorkbook()) {
            String subjSql = """
                SELECT s.id, s.subject_name, ts.form, ts.stream
                FROM teacher_subjects ts
                JOIN subjects s ON s.id = ts.subject_id
                WHERE ts.user_id = ?
                ORDER BY s.subject_name, ts.form, ts.stream
                """;
            try (PreparedStatement ps = db.getConnection().prepareStatement(subjSql)) {
                ps.setLong(1, teacherUserId);
                ResultSet rs = ps.executeQuery();
                boolean hasSheets = false;
                while (rs.next()) {
                    hasSheets = true;
                    String subjectName = rs.getString("subject_name");
                    int form = rs.getInt("form");
                    String stream = rs.getString("stream");
                    String sheetLabel = subjectName + " - Form" + form + stream;

                    Sheet sheet = wb.createSheet(sheetLabel);
                    Row hdr = sheet.createRow(0);
                    hdr.createCell(0).setCellValue("Admission No.");
                    hdr.createCell(1).setCellValue("Student Name");
                    hdr.createCell(2).setCellValue(subjectName + " Marks");

                    // Pre-populate students
                    try (PreparedStatement sp = db.getConnection().prepareStatement(
                            "SELECT admission_number, full_name FROM students WHERE form = ? AND stream = ? AND deallocated = 0 ORDER BY full_name")) {
                        sp.setInt(1, form);
                        sp.setString(2, stream);
                        ResultSet sr = sp.executeQuery();
                        int r = 1;
                        while (sr.next()) {
                            Row row = sheet.createRow(r++);
                            row.createCell(0).setCellValue(sr.getString("admission_number"));
                            row.createCell(1).setCellValue(sr.getString("full_name"));
                        }
                    }
                    sheet.autoSizeColumn(0);
                    sheet.autoSizeColumn(1);
                    sheet.autoSizeColumn(2);
                }
                if (!hasSheets) { UIUtils.showError("This teacher has no subject assignments."); return; }
            }

            try (FileOutputStream fos = new FileOutputStream(f)) { wb.write(fos); }
            UIUtils.showError("Template saved for " + teacherLabel + " with " + wb.getNumberOfSheets() + " subject sheets.");
        } catch (Exception e) {
            UIUtils.showError("Failed to generate template: " + e.getMessage());
        }
    }

    // ─── Utility ─────────────────────────────────────────────

    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> { double v = cell.getNumericCellValue();
                yield v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v); }
            default -> null;
        };
    }



    // ─── Data Classes ────────────────────────────────────────

    public static class SubjectRow {
        private final int num;
        private final long subjectId;
        private final String subjectName;
        private int outOf;
        private final int marksCount;
        private final String marksStatus;
        private final String publishedStatus;
        private final boolean published;
        private final String publishedBy;

        public SubjectRow(int num, long subjectId, String subjectName, int outOf, int marksCount,
                          String marksStatus, String publishedStatus, boolean published, String publishedBy) {
            this.num = num; this.subjectId = subjectId; this.subjectName = subjectName;
            this.outOf = outOf; this.marksCount = marksCount; this.marksStatus = marksStatus;
            this.publishedStatus = publishedStatus; this.published = published; this.publishedBy = publishedBy;
        }
        public int getNum() { return num; }
        public long getSubjectId() { return subjectId; }
        public String getSubjectName() { return subjectName; }
        public int getOutOf() { return outOf; }
        public void setOutOf(int v) { this.outOf = v; }
        public int getMarksCount() { return marksCount; }
        public String getMarksStatus() { return marksStatus; }
        public String getPublishedStatus() { return publishedStatus; }
        public boolean isPublished() { return published; }
        public String getPublishedBy() { return publishedBy; }
    }

    public static class MarkRow {
        private final int pos;
        private final long studentId;
        private final String admission;
        private final String name;
        private Double score;
        private String grade;
        private Integer points;
        private boolean dirty;

        public MarkRow(int pos, long studentId, String admission, String name,
                       Double score, String grade, Integer points, boolean dirty) {
            this.pos = pos; this.studentId = studentId; this.admission = admission;
            this.name = name; this.score = score; this.grade = grade;
            this.points = points; this.dirty = dirty;
        }
        public int getPos() { return pos; }
        public long getStudentId() { return studentId; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Double getScore() { return score; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
    }
}
