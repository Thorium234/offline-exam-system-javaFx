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
import javafx.util.converter.DoubleStringConverter;

import java.sql.*;
import java.util.*;

public class MarksEntryForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;

    private ComboBox<String> examBox;
    private ComboBox<Integer> formBox;
    private ComboBox<String> streamBox;
    private FlowPane subjectCardsArea;
    private VBox studentEntryArea;
    private Label selectedSubjectLabel;
    private TableView<StudentMarkRow> studentTable;
    private Button saveAllBtn;
    private Label statusLabel;

    private long selectedExamId;
    private long selectedSubjectId;
    private String selectedSubjectName;

    public MarksEntryForm(DatabaseEngine db) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);

        Label header = new Label("Marks Entry");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Select exam, class, and subject to enter marks. Grade & points auto-calculate.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox examRow = new HBox(10);
        examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        loadExams();
        examRow.getChildren().addAll(new Label("Exam:"), examBox);

        HBox classRow = new HBox(10);
        formBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Form");
        streamBox = new ComboBox<>();
        streamBox.setPromptText("Stream");
        streamBox.setPrefWidth(180);
        streamBox.setEditable(true);
        loadStreams();
        classRow.getChildren().addAll(new Label("Form:"), formBox, new Label("Stream:"), streamBox);

        Button loadBtn = new Button("Load Subjects");
        loadBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white;");
        classRow.getChildren().add(loadBtn);

        subjectCardsArea = new FlowPane(12, 12);
        subjectCardsArea.setPadding(new Insets(5, 0, 5, 0));
        subjectCardsArea.setVisible(false);

        studentEntryArea = new VBox(10);
        studentEntryArea.setVisible(false);

        HBox studentHeader = new HBox(10);
        selectedSubjectLabel = new Label();
        selectedSubjectLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        selectedSubjectLabel.setTextFill(Color.web("#1a237e"));
        Button backBtn = new Button("Back to Subjects");
        backBtn.setOnAction(e -> showSubjects());
        studentHeader.getChildren().addAll(selectedSubjectLabel, backBtn);
        studentEntryArea.getChildren().add(studentHeader);

        studentTable = new TableView<>();
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        studentTable.setEditable(true);
        studentTable.setPrefHeight(350);

        TableColumn<StudentMarkRow, Integer> colPos = new TableColumn<>("#");
        colPos.setCellValueFactory(new PropertyValueFactory<>("pos"));
        colPos.setPrefWidth(40);

        TableColumn<StudentMarkRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        colAdm.setPrefWidth(120);

        TableColumn<StudentMarkRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(220);

        TableColumn<StudentMarkRow, Double> colScore = new TableColumn<>("Score");
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colScore.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        colScore.setOnEditCommit(e -> {
            StudentMarkRow row = e.getRowValue();
            Double newVal = e.getNewValue();
            if (newVal != null && newVal >= 0) {
                row.score = newVal;
                String result = analysisService.determineGradeAndPoints(newVal, selectedSubjectId, selectedExamId);
                String[] parts = result.split("\\|");
                row.grade = parts[0];
                row.points = Integer.parseInt(parts[1]);
                row.dirty = true;
                studentTable.refresh();
            }
        });
        colScore.setPrefWidth(100);

        TableColumn<StudentMarkRow, String> colGrade = new TableColumn<>("Grade");
        colGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));
        colGrade.setPrefWidth(70);

        TableColumn<StudentMarkRow, Integer> colPoints = new TableColumn<>("Points");
        colPoints.setCellValueFactory(new PropertyValueFactory<>("points"));
        colPoints.setPrefWidth(70);

        TableColumn<StudentMarkRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setPrefWidth(90);

        studentTable.getColumns().addAll(colPos, colAdm, colName, colScore, colGrade, colPoints, colStatus);

        HBox saveRow = new HBox(10);
        saveAllBtn = new Button("Save All Marks");
        saveAllBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        Button refreshBtn = new Button("Refresh");
        statusLabel = new Label();
        statusLabel.setTextFill(Color.gray(0.5));
        saveRow.getChildren().addAll(saveAllBtn, refreshBtn, statusLabel);

        studentEntryArea.getChildren().addAll(studentTable, saveRow);

        view.getChildren().addAll(header, info, examRow, classRow, subjectCardsArea, studentEntryArea);

        loadBtn.setOnAction(e -> loadSubjects());
        saveAllBtn.setOnAction(e -> saveAllMarks());
        refreshBtn.setOnAction(e -> {
            if (selectedSubjectId > 0) loadStudents(selectedSubjectId);
        });

        return view;
    }

    private void loadSubjects() {
        if (examBox.getValue() == null) { showAlert("Select an exam."); return; }
        if (formBox.getValue() == null) { showAlert("Select a form."); return; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) { showAlert("Select a stream."); return; }

        selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        studentEntryArea.setVisible(false);
        subjectCardsArea.getChildren().clear();

        int form = formBox.getValue();
        String stream = streamBox.getValue();
        int studentCount = countStudents(form, stream);
        if (studentCount == 0) {
            showAlert("No students found in Form " + form + " - " + stream);
            return;
        }

        Map<String, Object[]> subjects = getSubjectsWithMarksCount();
        if (subjects.isEmpty()) {
            showAlert("No subjects defined. Add subjects first.");
            return;
        }

        for (var entry : subjects.entrySet()) {
            String name = entry.getKey();
            Object[] info = entry.getValue();
            long subjId = (Long) info[0];
            String code = (String) info[1];
            String dept = (String) info[2];
            int markCount = (Integer) info[3];

            VBox card = new VBox(4);
            card.setPrefSize(180, 90);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-border-color: #e0e0e0; -fx-border-radius: 10; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);");

            Label nameLbl = new Label(name);
            nameLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
            nameLbl.setWrapText(true);
            nameLbl.setAlignment(Pos.CENTER);

            Label codeLbl = new Label(code + " | " + dept);
            codeLbl.setFont(Font.font("System", 10));
            codeLbl.setTextFill(Color.gray(0.5));

            Label countLbl = new Label(markCount + " marks entered");
            countLbl.setFont(Font.font("System", 10));
            countLbl.setTextFill(markCount > 0 ? Color.web("#2e7d32") : Color.gray(0.5));

            card.getChildren().addAll(nameLbl, codeLbl, countLbl);

            String fg = markCount > 0 ? "#e8f5e9" : "#f5f5f5";
            card.setStyle(card.getStyle() + "-fx-background-color: " + fg + ";");

            card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle().replace("-fx-border-color: #e0e0e0", "-fx-border-color: #1a237e")
                    .replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2)",
                        "-fx-effect: dropshadow(gaussian, rgba(26,35,126,0.2), 8, 0, 0, 2)")));
            card.setOnMouseExited(e ->
                card.setStyle(card.getStyle().replace("-fx-border-color: #1a237e", "-fx-border-color: #e0e0e0")
                    .replace("-fx-effect: dropshadow(gaussian, rgba(26,35,126,0.2), 8, 0, 0, 2)",
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2)")));

            long sid = subjId;
            card.setOnMouseClicked(e -> loadStudents(sid));
            subjectCardsArea.getChildren().add(card);
        }

        subjectCardsArea.setVisible(true);
        statusLabel.setText("Class: Form " + form + " - " + stream + " | " + studentCount + " students");
    }

    private void loadStudents(long subjectId) {
        selectedSubjectId = subjectId;
        selectedSubjectName = getSubjectName(subjectId);
        selectedSubjectLabel.setText(selectedSubjectName);
        studentEntryArea.setVisible(true);
        subjectCardsArea.setVisible(false);

        int form = formBox.getValue();
        String stream = streamBox.getValue();

        ObservableList<StudentMarkRow> rows = FXCollections.observableArrayList();
        String sql = """
            SELECT s.id, s.admission_number, s.full_name, m.score, m.grade_achieved, m.points_achieved
            FROM students s
            LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? AND m.subject_id = ?
            WHERE s.form = ? AND s.stream = ? AND s.deallocated = 0
            ORDER BY s.full_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ResultSet rs = ps.executeQuery();
            int pos = 0;
            while (rs.next()) {
                pos++;
                rows.add(new StudentMarkRow(
                    pos,
                    rs.getLong("id"),
                    rs.getString("admission_number"),
                    rs.getString("full_name"),
                    rs.getObject("score") != null ? rs.getDouble("score") : null,
                    rs.getString("grade_achieved"),
                    rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null,
                    rs.getObject("score") != null ? "Saved" : ""
                ));
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }
        studentTable.setItems(rows);
        statusLabel.setText(selectedSubjectName + " | " + rows.size() + " students");
    }

    private void saveAllMarks() {
        long examId = selectedExamId;
        long subjectId = selectedSubjectId;
        if (examId == 0 || subjectId == 0) return;

        int saved = 0;
        int errors = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (StudentMarkRow row : studentTable.getItems()) {
                    if (row.score == null || !row.dirty) continue;
                    ps.setLong(1, examId);
                    ps.setLong(2, row.studentId);
                    ps.setLong(3, subjectId);
                    ps.setDouble(4, row.score);
                    ps.setString(5, row.grade);
                    ps.setInt(6, row.points);
                    ps.addBatch();
                    saved++;
                }
                ps.executeBatch();
                conn.commit();
                String msg = "Saved " + saved + " mark(s)";
                if (errors > 0) msg += " (" + errors + " errors)";
                statusLabel.setText(msg);
                loadStudents(subjectId);
            } catch (Exception e) {
                conn.rollback();
                showAlert("Failed to save: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showAlert("DB error: " + e.getMessage());
        }
    }

    private void showSubjects() {
        studentEntryArea.setVisible(false);
        subjectCardsArea.setVisible(true);
        loadSubjects();
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

    private void loadStreams() {
        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM students ORDER BY stream")) {
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        streamBox.setItems(FXCollections.observableArrayList(streams));
    }

    private Map<String, Object[]> getSubjectsWithMarksCount() {
        Map<String, Object[]> map = new LinkedHashMap<>();
        String sql = """
            SELECT sub.id, sub.subject_code, sub.subject_name, sub.department,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = sub.id) AS mark_count
            FROM subjects sub
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedExamId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                map.put(rs.getString("subject_name"), new Object[]{
                    rs.getLong("id"),
                    rs.getString("subject_code"),
                    rs.getString("department"),
                    rs.getInt("mark_count")
                });
        } catch (SQLException e) { showAlert(e.getMessage()); }
        return map;
    }

    private String getSubjectName(long subjectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT subject_name FROM subjects WHERE id = ?")) {
            ps.setLong(1, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("subject_name");
        } catch (SQLException e) { showAlert(e.getMessage()); }
        return "Subject";
    }

    private int countStudents(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM students WHERE form = ? AND stream = ? AND deallocated = 0")) {
            ps.setInt(1, form); ps.setString(2, stream);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }

    public static class StudentMarkRow {
        private final int pos;
        private final long studentId;
        private final String admission;
        private final String name;
        private Double score;
        private String grade;
        private Integer points;
        private String status;
        private boolean dirty;

        public StudentMarkRow(int pos, long studentId, String admission, String name,
                              Double score, String grade, Integer points, String status) {
            this.pos = pos;
            this.studentId = studentId;
            this.admission = admission;
            this.name = name;
            this.score = score;
            this.grade = grade;
            this.points = points;
            this.status = status;
            this.dirty = false;
        }

        public int getPos() { return pos; }
        public long getStudentId() { return studentId; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Double getScore() { return score; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
        public String getStatus() { return status; }
    }
}
