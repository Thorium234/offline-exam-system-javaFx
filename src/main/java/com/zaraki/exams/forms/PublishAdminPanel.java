package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.service.ExamAnalysisServiceImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class PublishAdminPanel {

    private final DatabaseEngine db;
    private long currentExamId;
    private final String username;

    private final VBox root = new VBox(15);
    private final TableView<SubjectRow> subjectTable = new TableView<>();
    private final ObservableList<SubjectRow> subjectData = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();
    private Runnable onRefresh;

    public PublishAdminPanel(DatabaseEngine db, String username) {
        this.db = db;
        this.username = username;
    }

    public Node getView() {
        root.setPadding(new Insets(15));
        buildSubjectTable();
        return root;
    }

    public void load(long examId, Runnable onRefresh) {
        this.currentExamId = examId;
        this.onRefresh = onRefresh;
        loadSubjectStatus();
    }

    private void buildSubjectTable() {
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.setItems(subjectData);
        subjectTable.setPrefHeight(300);

        TableColumn<SubjectRow, Integer> colNum = new TableColumn<>("#");
        colNum.setCellValueFactory(new PropertyValueFactory<>("num")); colNum.setPrefWidth(40);

        TableColumn<SubjectRow, String> colSubj = new TableColumn<>("Subject");
        colSubj.setCellValueFactory(new PropertyValueFactory<>("subjectName")); colSubj.setPrefWidth(180);

        TableColumn<SubjectRow, Integer> colOutOf = new TableColumn<>("Out Of");
        colOutOf.setCellValueFactory(new PropertyValueFactory<>("outOf"));
        colOutOf.setCellFactory(TextFieldTableCell.forTableColumn(new javafx.util.converter.IntegerStringConverter()));
        colOutOf.setOnEditCommit(e -> {
            SubjectRow row = e.getRowValue();
            int v = Math.max(1, e.getNewValue());
            row.setOutOf(v);
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE exam_subjects SET out_of = ? WHERE exam_id = ? AND subject_id = ?")) {
                ps.setInt(1, v); ps.setLong(2, currentExamId); ps.setLong(3, row.subjectId);
                ps.executeUpdate();
            } catch (SQLException ex) { UIUtils.showError("Failed to update out_of: " + ex.getMessage()); }
        });
        colOutOf.setPrefWidth(70);

        TableColumn<SubjectRow, String> colMarks = new TableColumn<>("Marks Status");
        colMarks.setCellValueFactory(new PropertyValueFactory<>("marksStatus")); colMarks.setPrefWidth(120);

        TableColumn<SubjectRow, String> colPub = new TableColumn<>("Published");
        colPub.setCellValueFactory(new PropertyValueFactory<>("publishedStatus")); colPub.setPrefWidth(120);

        TableColumn<SubjectRow, Void> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(180);
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button upBtn = createBtn("Upload Marks", "#1565c0");
            private final Button pubBtn = createBtn("Publish", "#2e7d32");
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                SubjectRow row = (SubjectRow) getTableRow().getItem();
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER);
                if (!row.published) {
                    Button up = row.marksCount > 0 ? new Button("Re-upload") : new Button("Upload Marks");
                    up.setStyle(upBtn.getStyle()); up.setOnAction(e -> fireUpload(row)); box.getChildren().add(up);
                    if (row.marksCount > 0) {
                        Button pub = new Button("Publish");
                        pub.setStyle(pubBtn.getStyle()); pub.setOnAction(e -> publishSubject(row)); box.getChildren().add(pub);
                    }
                } else {
                    Label done = new Label("Done");
                    done.setTextFill(Color.GREEN); done.setFont(Font.font("System", FontWeight.BOLD, 12));
                    Label by = new Label("by " + row.publishedBy);
                    by.setFont(Font.font("System", 10)); by.setTextFill(Color.gray(0.5));
                    box.getChildren().addAll(done, by);
                }
                setGraphic(box);
            }
        });
        subjectTable.getColumns().addAll(colNum, colSubj, colOutOf, colMarks, colPub, colAction);
        subjectTable.setEditable(true);

        HBox actionRow = new HBox(10);
        Button releaseBtn = new Button("Release Exam (Admin Only)");
        releaseBtn.getStyleClass().addAll("button", "button-danger", "button-lg");
        releaseBtn.setPrefWidth(300);
        releaseBtn.setOnAction(e -> releaseExam());

        Button multiUploadBtn = new Button("Upload Multi-Sheet Excel (Admin)");
        multiUploadBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");
        multiUploadBtn.setOnAction(e -> uploadMultiSheetExcel());

        Button teacherTemplateBtn = new Button("Generate Teacher Template");
        teacherTemplateBtn.setOnAction(e -> generateTeacherTemplate());

        actionRow.getChildren().addAll(releaseBtn, multiUploadBtn, teacherTemplateBtn);

        root.getChildren().addAll(subjectTable, actionRow, statusLabel);
    }

    private Button createBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 11;");
        return b;
    }

    private void loadSubjectStatus() {
        subjectData.clear();
        String ensureSql = "INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) SELECT ?, id, 100 FROM subjects";
        try (PreparedStatement ps = db.getConnection().prepareStatement(ensureSql)) {
            ps.setLong(1, currentExamId); ps.executeUpdate();
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); return; }

        String sql = """
            SELECT s.id, s.subject_name, s.subject_code,
                   COALESCE(es.out_of, 100) AS out_of, COALESCE(es.published, 0) AS published,
                   es.published_by, es.uploaded_by, es.uploaded_at,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = s.id) AS marks_count
            FROM subjects s
            LEFT JOIN exam_subjects es ON es.exam_id = ? AND es.subject_id = s.id
            ORDER BY s.subject_name
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, currentExamId); ps.setLong(2, currentExamId);
            ResultSet rs = ps.executeQuery();
            int num = 0;
            while (rs.next()) {
                num++;
                int marksCount = rs.getInt("marks_count");
                boolean published = rs.getInt("published") == 1;
                subjectData.add(new SubjectRow(num, rs.getLong("id"), rs.getString("subject_name"),
                    rs.getInt("out_of"), marksCount,
                    marksCount > 0 ? (published ? "Published" : "Uploaded") : "Not Uploaded",
                    published ? "Yes" : "No", published, rs.getString("published_by")));
            }
            String released = isExamReleased(currentExamId) ? " (RELEASED)" : "";
            statusLabel.setText("Exam: " + released + " | " + num + " subjects");
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
    }

    private boolean isExamReleased(long examId) {
        String sql = "SELECT released FROM exams WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("released") == 1;
        } catch (SQLException e) { return false; }
    }

    private Runnable uploadCallback;

    public void setUploadCallback(Runnable cb) { this.uploadCallback = cb; }

    private void fireUpload(SubjectRow row) {
        if (uploadCallback != null) uploadCallback.run();
    }

    private void publishSubject(SubjectRow row) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE exam_subjects SET published = 1, published_by = ?, published_at = ? WHERE exam_id = ? AND subject_id = ?")) {
            ps.setString(1, username);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            ps.setLong(3, currentExamId); ps.setLong(4, row.subjectId);
            int updated = ps.executeUpdate();
            if (updated > 0) { UIUtils.showError("Subject \"" + row.subjectName + "\" published."); loadSubjectStatus(); }
        } catch (SQLException e) { UIUtils.showError("Failed to publish: " + e.getMessage()); }
    }

    private void releaseExam() {
        String checkSql = "SELECT COUNT(*) FROM exam_subjects WHERE exam_id = ? AND published = 0";
        try (PreparedStatement ps = db.getConnection().prepareStatement(checkSql)) {
            ps.setLong(1, currentExamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                UIUtils.showError("All subjects must be published. " + rs.getInt(1) + " subject(s) not yet published.");
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
        } catch (SQLException e) { UIUtils.showError("Failed to release: " + e.getMessage()); }
    }

    private void uploadMultiSheetExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Multi-Sheet Marks Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        if (f.length() > 10_485_760) { UIUtils.showError("File too large."); return; }

        long examId = currentExamId;
        List<String> errors = new ArrayList<>();
        int totalImported = 0, totalRows = 0;

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = wb.getSheetName(s);
                if (sheetName == null || sheetName.isBlank()) continue;
                String subjectName;
                Integer form = null; String stream = null;
                if (sheetName.contains("-")) {
                    String[] parts = sheetName.split("-", 2);
                    subjectName = parts[0].trim();
                    String classPart = parts[1].trim().toUpperCase().replace("FORM ", "").replace("FORM", "").trim();
                    StringBuilder formSb = new StringBuilder();
                    int ci = 0;
                    while (ci < classPart.length() && Character.isDigit(classPart.charAt(ci))) formSb.append(classPart.charAt(ci++));
                    if (formSb.length() > 0) { form = Integer.parseInt(formSb.toString()); stream = classPart.substring(ci).trim(); }
                } else { subjectName = sheetName.trim(); }
                if (subjectName.isEmpty()) { errors.add("Sheet '" + sheetName + "': could not parse subject name"); continue; }
                long subjectId;
                try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT id FROM subjects WHERE subject_name = ?")) {
                    ps.setString(1, subjectName);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) { errors.add("Sheet '" + sheetName + "': unknown subject '" + subjectName + "'"); continue; }
                    subjectId = rs.getLong("id");
                }
                Map<String, Long> studentMap = new HashMap<>();
                boolean hasClassFilter = form != null && stream != null && !stream.isEmpty();
                try (PreparedStatement ps = hasClassFilter
                         ? db.getConnection().prepareStatement(
                             "SELECT id, admission_number FROM students WHERE form = ? AND stream = ? AND deallocated = 0")
                         : db.getConnection().prepareStatement(
                             "SELECT id, admission_number FROM students WHERE deallocated = 0")) {
                    if (hasClassFilter) { ps.setInt(1, form); ps.setString(2, stream); }
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) studentMap.put(rs.getString("admission_number"), rs.getLong("id"));
                }
                try (PreparedStatement ps = db.getConnection().prepareStatement("INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) VALUES (?,?,100)")) {
                    ps.setLong(1, examId); ps.setLong(2, subjectId); ps.executeUpdate();
                }
                int subjectOutOf = 100;
                try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
                    ps.setLong(1, examId); ps.setLong(2, subjectId);
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
                        if (studentId == null) { errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": student " + adm + " not found"); continue; }
                        String scoreStr = getCellString(row.getCell(2));
                        if (scoreStr == null || scoreStr.isBlank()) continue;
                        double score;
                        try { score = Double.parseDouble(scoreStr.trim()); }
                        catch (NumberFormatException e) { errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": invalid score"); continue; }
                        if (!Double.isFinite(score) || score < 0 || score > subjectOutOf) {
                            errors.add("Sheet '" + sheetName + "', row " + (r + 1) + ": score must be between 0 and " + subjectOutOf);
                            continue;
                        }
                        IExamAnalysisService eas = new ExamAnalysisServiceImpl();
                        String gradeResult = eas.determineGradeAndPoints(score, subjectId, examId);
                        String[] parts = gradeResult.split("\\|");
                        ps.setLong(1, examId); ps.setLong(2, studentId); ps.setLong(3, subjectId);
                        ps.setDouble(4, score); ps.setString(5, parts[0]); ps.setInt(6, Integer.parseInt(parts[1]));
                        ps.addBatch(); totalImported++;
                        if (totalImported % 200 == 0) ps.executeBatch();
                    }
                    ps.executeBatch();
                }
            }
            String msg = "Imported " + totalImported + " marks from " + totalRows + " rows across " + wb.getNumberOfSheets() + " sheets";
            if (!errors.isEmpty()) msg += " (" + errors.size() + " errors)";
            UIUtils.showError(msg);
            loadSubjectStatus();
        } catch (Exception e) { UIUtils.showError("Multi-sheet upload failed: " + e.getMessage()); }
    }

    private void generateTeacherTemplate() {
        ComboBox<String> teacherSelector = new ComboBox<>();
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, full_name FROM users WHERE role = 'teacher' ORDER BY full_name")) {
            while (rs.next()) teacherSelector.getItems().add(rs.getLong("id") + ":" + rs.getString("username") + " | " + rs.getString("full_name"));
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
            String subjSql = "SELECT s.id, s.subject_name, ts.form, ts.stream FROM teacher_subjects ts JOIN subjects s ON s.id = ts.subject_id WHERE ts.user_id = ? ORDER BY s.subject_name, ts.form, ts.stream";
            try (PreparedStatement ps = db.getConnection().prepareStatement(subjSql)) {
                ps.setLong(1, teacherUserId);
                ResultSet rs = ps.executeQuery();
                boolean hasSheets = false;
                while (rs.next()) {
                    hasSheets = true;
                    String sheetLabel = rs.getString("subject_name") + " - Form" + rs.getInt("form") + rs.getString("stream");
                    Sheet sheet = wb.createSheet(sheetLabel);
                    Row hdr = sheet.createRow(0);
                    hdr.createCell(0).setCellValue("Admission No.");
                    hdr.createCell(1).setCellValue("Student Name");
                    hdr.createCell(2).setCellValue(rs.getString("subject_name") + " Marks");
                    try (PreparedStatement sp = db.getConnection().prepareStatement(
                            "SELECT admission_number, full_name FROM students WHERE form = ? AND stream = ? AND deallocated = 0 ORDER BY full_name")) {
                        sp.setInt(1, rs.getInt("form")); sp.setString(2, rs.getString("stream"));
                        ResultSet sr = sp.executeQuery();
                        int r = 1;
                        while (sr.next()) {
                            Row row = sheet.createRow(r++);
                            row.createCell(0).setCellValue(sr.getString("admission_number"));
                            row.createCell(1).setCellValue(sr.getString("full_name"));
                        }
                    }
                    sheet.autoSizeColumn(0); sheet.autoSizeColumn(1); sheet.autoSizeColumn(2);
                }
                if (!hasSheets) { UIUtils.showError("This teacher has no subject assignments."); return; }
            }
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) { wb.write(fos); }
            UIUtils.showError("Template saved for " + teacherLabel + " with " + wb.getNumberOfSheets() + " subject sheets.");
        } catch (Exception e) { UIUtils.showError("Failed to generate template: " + e.getMessage()); }
    }

    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> { double v = cell.getNumericCellValue(); yield v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v); }
            default -> null;
        };
    }

    public static class SubjectRow {
        private final int num; private final long subjectId; private final String subjectName;
        private int outOf; private final int marksCount; private final String marksStatus;
        private final String publishedStatus; private final boolean published; private final String publishedBy;
        public SubjectRow(int num, long subjectId, String subjectName, int outOf, int marksCount,
                          String marksStatus, String publishedStatus, boolean published, String publishedBy) {
            this.num = num; this.subjectId = subjectId; this.subjectName = subjectName; this.outOf = outOf;
            this.marksCount = marksCount; this.marksStatus = marksStatus;
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
}
