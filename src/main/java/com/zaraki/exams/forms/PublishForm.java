package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
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
    private final String username;
    private final String role;

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

    public PublishForm(DatabaseEngine db, String username, String role) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
        this.username = username;
        this.role = role;
    }

    public VBox getView() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label header = new Label("Publish Management");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Upload marks per subject, publish each subject, then release the exam for report generation.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

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
            } catch (SQLException ex) { showAlert("Failed to update out_of: " + ex.getMessage()); }
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

        // Release button area
        Button releaseBtn = new Button("Release Exam (Admin Only)");
        releaseBtn.setStyle("-fx-background-color: #e65100; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        releaseBtn.setPrefWidth(300);
        releaseBtn.setOnAction(e -> releaseExam());

        VBox tableWrapper = new VBox(10, subjectTable, releaseBtn);
        subjectTableArea.getChildren().add(tableWrapper);
    }

    private void loadExams() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadSubjectStatus() {
        if (examBox.getValue() == null) { showAlert("Select an exam."); return; }
        currentExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        uploadArea.setVisible(false);
        subjectData.clear();

        // Ensure exam_subjects rows exist for all subjects
        String ensureSql = "INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) SELECT ?, id, 100 FROM subjects";
        try (PreparedStatement ps = db.getConnection().prepareStatement(ensureSql)) {
            ps.setLong(1, currentExamId);
            ps.executeUpdate();
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        String sql = """
            SELECT s.id, s.subject_name, s.subject_code,
                   COALESCE(es.out_of, 100) AS out_of,
                   COALESCE(es.published, 0) AS published,
                   es.published_by, es.uploaded_by, es.uploaded_at,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = s.id) AS marks_count
            FROM subjects s
            LEFT JOIN exam_subjects es ON es.exam_id = ? AND es.subject_id = s.id
            ORDER BY s.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, currentExamId);
            ps.setLong(2, currentExamId);
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
        } catch (SQLException e) { showAlert(e.getMessage()); }
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
            if (v != null && v >= 0) {
                mr.score = v;
                mr.dirty = true;
                uploadTable.refresh();
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
        } catch (SQLException e) { showAlert(e.getMessage()); }
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
                showAlert("Failed to save: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showAlert("DB error: " + e.getMessage());
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
            showAlert("Template saved.");
        } catch (Exception e) {
            showAlert("Failed to save template: " + e.getMessage());
        }
    }

    private void uploadExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Marks Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;

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

                    // Resolve student
                    long studentId;
                    try (PreparedStatement sp = conn.prepareStatement("SELECT id FROM students WHERE admission_number = ?")) {
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
                    showAlert(sb.toString());
                }
            }
        } catch (Exception e) {
            showAlert("Excel import failed: " + e.getMessage());
        }
    }

    // ─── Publish / Release ──────────────────────────────────

    private void publishSubject(SubjectRow row) {
        if (!role.equals("teacher") && !role.equals("admin")) {
            showAlert("Only teachers and admins can publish subjects.");
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
                showAlert("Subject \"" + row.subjectName + "\" published.");
                loadSubjectStatus();
            }
        } catch (SQLException e) {
            showAlert("Failed to publish: " + e.getMessage());
        }
    }

    private void releaseExam() {
        if (!role.equals("admin")) {
            showAlert("Only administrators can release an exam.");
            return;
        }
        // Check all subjects published
        String checkSql = "SELECT COUNT(*) FROM exam_subjects WHERE exam_id = ? AND published = 0";
        try (PreparedStatement ps = db.getConnection().prepareStatement(checkSql)) {
            ps.setLong(1, currentExamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                showAlert("All subjects must be published before releasing the exam. " + rs.getInt(1) + " subject(s) not yet published.");
                return;
            }
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE exams SET released = 1, released_by = ?, released_at = ? WHERE id = ?")) {
            ps.setString(1, username);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            ps.setLong(3, currentExamId);
            ps.executeUpdate();
            showAlert("Exam released. Reports can now be generated.");
            loadSubjectStatus();
        } catch (SQLException e) {
            showAlert("Failed to release: " + e.getMessage());
        }
    }

    public static boolean isExamReleased(long examId) {
        try (Connection conn = DatabaseEngine.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT released FROM exams WHERE id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("released") == 1;
        } catch (SQLException e) { return false; }
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

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait());
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
