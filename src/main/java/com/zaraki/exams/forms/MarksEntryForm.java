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
    private final long loggedInUserId;
    private final boolean isTeacher;

    private ComboBox<String> examBox;
    private ComboBox<String> subjectBox;
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
    private int selectedOutOf;
    private String selectedSubjectName;

    // Teacher-mode nodes
    private HBox teacherSubjectRow;
    private Button teacherLoadBtn;

    public MarksEntryForm(DatabaseEngine db, long loggedInUserId, String loggedInRole) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
        this.loggedInUserId = loggedInUserId;
        this.isTeacher = "teacher".equals(loggedInRole);
    }

    public VBox getView() {
        VBox view = new VBox(15);

        Label header = new Label("Marks Entry");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label(isTeacher
            ? "Select exam, subject, then stream. Grade & points auto-calculate."
            : "Select exam, class, and subject to enter marks. Grade & points auto-calculate.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox examRow = new HBox(10);
        examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        loadExams();
        examRow.getChildren().addAll(new Label("Exam:"), examBox);

        // Teacher-mode: Subject + Form + Stream (filtered)
        teacherSubjectRow = new HBox(10);
        subjectBox = new ComboBox<>();
        subjectBox.setPromptText("Select Subject");
        subjectBox.setPrefWidth(250);
        teacherSubjectRow.getChildren().addAll(new Label("Subject:"), subjectBox);

        HBox classRow = new HBox(10);
        formBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Form");
        streamBox = new ComboBox<>();
        streamBox.setPromptText("Stream");
        streamBox.setPrefWidth(180);
        streamBox.setEditable(true);

        teacherLoadBtn = new Button("Load Students");
        teacherLoadBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white;");
        teacherLoadBtn.setDisable(true);

        classRow.getChildren().addAll(new Label("Form:"), formBox, new Label("Stream:"), streamBox);

        // Admin-mode: shared classRow + load subjects button
        Button loadBtn = new Button("Load Subjects");
        loadBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white;");

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
            if (newVal != null && Double.isFinite(newVal) && newVal >= 0 && newVal <= selectedOutOf) {
                row.score = newVal;
                String result = analysisService.determineGradeAndPoints(newVal, selectedSubjectId, selectedExamId);
                String[] parts = result.split("\\|");
                row.grade = parts[0];
                row.points = Integer.parseInt(parts[1]);
                row.dirty = true;
                studentTable.refresh();
            } else if (newVal != null && !Double.isFinite(newVal)) {
                showAlert("Invalid score value.");
            } else if (newVal != null) {
                showAlert("Score must be between 0 and " + selectedOutOf + ".");
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
        colStatus.setPrefWidth(80);
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(
                FXCollections.observableArrayList("P", "A", "D"));
            { combo.setOnAction(e -> {
                StudentMarkRow row = getTableRow().getItem();
                if (row != null) { row.status = combo.getValue(); row.dirty = true; }
            }); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    combo.setValue(getTableRow().getItem().status != null ? getTableRow().getItem().status : "P");
                    setGraphic(combo);
                }
            }
        });

        TableColumn<StudentMarkRow, String> colCmt = new TableColumn<>("Comment");
        colCmt.setCellValueFactory(new PropertyValueFactory<>("teacherComment"));
        colCmt.setCellFactory(TextFieldTableCell.forTableColumn());
        colCmt.setOnEditCommit(e -> {
            StudentMarkRow row = e.getRowValue();
            row.teacherComment = e.getNewValue();
            row.dirty = true;
        });
        colCmt.setPrefWidth(150);

        TableColumn<StudentMarkRow, String> colDev = new TableColumn<>("Deviation");
        colDev.setCellValueFactory(new PropertyValueFactory<>("deviation"));
        colDev.setPrefWidth(80);

        studentTable.getColumns().addAll(colPos, colAdm, colName, colScore, colGrade, colPoints,
            colStatus, colCmt, colDev);

        HBox saveRow = new HBox(10);
        saveAllBtn = new Button("Save All Marks");
        saveAllBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        Button refreshBtn = new Button("Refresh");
        statusLabel = new Label();
        statusLabel.setTextFill(Color.gray(0.5));
        saveRow.getChildren().addAll(saveAllBtn, refreshBtn, statusLabel);

        studentEntryArea.getChildren().addAll(studentTable, saveRow);

        if (isTeacher) {
            classRow.getChildren().add(teacherLoadBtn);
            view.getChildren().addAll(header, info, examRow, teacherSubjectRow, classRow, studentEntryArea);
            setupTeacherActions();
        } else {
            classRow.getChildren().add(loadBtn);
            view.getChildren().addAll(header, info, examRow, classRow, subjectCardsArea, studentEntryArea);
            loadBtn.setOnAction(e -> loadSubjects());
        }

        saveAllBtn.setOnAction(e -> saveAllMarks());
        refreshBtn.setOnAction(e -> {
            if (selectedSubjectId > 0) loadStudents(selectedSubjectId, selectedOutOf);
        });

        return view;
    }

    private void setupTeacherActions() {
        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            loadTeacherSubjects();
        });

        subjectBox.setOnAction(e -> {
            if (subjectBox.getValue() == null) return;
            teacherLoadBtn.setDisable(true);
            loadTeacherForms();
        });

        formBox.setOnAction(e -> {
            if (formBox.getValue() == null) return;
            loadTeacherStreams();
        });

        teacherLoadBtn.setOnAction(e -> loadTeacherMarks());
    }

    private void loadTeacherSubjects() {
        subjectBox.getItems().clear();
        String sql = """
            SELECT DISTINCT s.id, s.subject_code, s.subject_name
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, loggedInUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                subjectBox.getItems().add(rs.getLong("id") + ":" + rs.getString("subject_code") + " - " + rs.getString("subject_name"));
        } catch (SQLException ex) { showAlert("Failed to load subjects: " + ex.getMessage()); }
    }

    private void loadTeacherForms() {
        formBox.setValue(null);
        streamBox.getItems().clear();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
        selectedSubjectId = subjectId;
        selectedSubjectName = subjectBox.getValue().split(" - ")[1];

        Set<Integer> forms = new TreeSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT form FROM teacher_subjects WHERE user_id = ? AND subject_id = ? ORDER BY form")) {
            ps.setLong(1, loggedInUserId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (SQLException ex) { showAlert("Failed to load forms: " + ex.getMessage()); }
        formBox.setItems(FXCollections.observableArrayList(forms));
    }

    private void loadTeacherStreams() {
        streamBox.getItems().clear();
        int form = formBox.getValue();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);

        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT stream FROM teacher_subjects WHERE user_id = ? AND subject_id = ? AND form = ? ORDER BY stream")) {
            ps.setLong(1, loggedInUserId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException ex) { showAlert("Failed to load streams: " + ex.getMessage()); }

        if (streams.size() == 1) {
            streamBox.setItems(FXCollections.observableArrayList(streams));
            streamBox.setValue(streams.iterator().next());
            teacherLoadBtn.setDisable(false);
        } else if (streams.size() > 1) {
            streamBox.setItems(FXCollections.observableArrayList(streams));
        } else {
            streamBox.setItems(FXCollections.observableArrayList());
            teacherLoadBtn.setDisable(true);
        }
    }

    private void loadTeacherMarks() {
        if (examBox.getValue() == null) { showAlert("Select an exam."); return; }
        if (subjectBox.getValue() == null) { showAlert("Select a subject."); return; }
        if (formBox.getValue() == null) { showAlert("Select a form."); return; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) { showAlert("Select a stream."); return; }

        selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        selectedSubjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);

        int outOf = getOutOf(selectedExamId, selectedSubjectId);
        if (outOf <= 0) outOf = 100;
        selectedOutOf = outOf;

        loadStudents(selectedSubjectId, selectedOutOf);
    }

    private int getOutOf(long examId, long subjectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT out_of FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("out_of") : 100;
        } catch (SQLException e) { return 100; }
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
            int outOf = (Integer) info[3];
            int markCount = (Integer) info[4];

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
            int oo = outOf;
            card.setOnMouseClicked(e -> loadStudents(sid, oo));
            subjectCardsArea.getChildren().add(card);
        }

        subjectCardsArea.setVisible(true);
        statusLabel.setText("Class: Form " + form + " - " + stream + " | " + studentCount + " students");
    }

    private void loadStudents(long subjectId, int outOf) {
        selectedSubjectId = subjectId;
        selectedOutOf = outOf;
        selectedSubjectName = getSubjectName(subjectId);
        selectedSubjectLabel.setText(selectedSubjectName);
        studentEntryArea.setVisible(true);
        subjectCardsArea.setVisible(false);

        int form = formBox.getValue();
        String stream = streamBox.getValue();

        ObservableList<StudentMarkRow> rows = FXCollections.observableArrayList();
        double classAvg = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT AVG(score) FROM marks WHERE exam_id = ? AND subject_id = ?")) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) classAvg = rs.getDouble(1);
        } catch (SQLException e) { /* ignore */ }

        String sql = """
            SELECT s.id, s.admission_number, s.full_name,
                   m.score, m.grade_achieved, m.points_achieved,
                   m.status, m.teacher_comment, m.deviation
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
            double finalClassAvg = classAvg;
            while (rs.next()) {
                pos++;
                Double scoreVal = rs.getObject("score") != null ? rs.getDouble("score") : null;
                String savedStatus = rs.getString("status");
                String status = savedStatus != null ? savedStatus
                    : (scoreVal != null ? "P" : "");
                String comment = rs.getString("teacher_comment");
                Double deviation = null;
                if (scoreVal != null) {
                    deviation = rs.getObject("deviation") != null ? rs.getDouble("deviation")
                        : (finalClassAvg > 0 ? scoreVal - finalClassAvg : null);
                }
                rows.add(new StudentMarkRow(
                    pos,
                    rs.getLong("id"),
                    rs.getString("admission_number"),
                    rs.getString("full_name"),
                    scoreVal,
                    rs.getString("grade_achieved"),
                    rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null,
                    status,
                    comment != null ? comment : "",
                    deviation != null ? String.format("%+.1f", deviation) : ""
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
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved, status, teacher_comment, deviation) VALUES (?,?,?,?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (StudentMarkRow row : studentTable.getItems()) {
                    if (!row.dirty) continue;
                    String status = (row.status != null && !row.status.isEmpty()) ? row.status : "P";
                    double deviation = 0;
                    if (row.score != null) {
                        double avg = getClassAverage(examId, subjectId);
                        deviation = Math.round((row.score - avg) * 10.0) / 10.0;
                    }
                    ps.setLong(1, examId);
                    ps.setLong(2, row.studentId);
                    ps.setLong(3, subjectId);
                    if (row.score != null) {
                        ps.setDouble(4, row.score);
                    } else {
                        ps.setNull(4, java.sql.Types.REAL);
                    }
                    ps.setString(5, row.grade);
                    if (row.points != null) {
                        ps.setInt(6, row.points);
                    } else {
                        ps.setNull(6, java.sql.Types.INTEGER);
                    }
                    ps.setString(7, status);
                    ps.setString(8, row.teacherComment);
                    ps.setDouble(9, deviation);
                    ps.addBatch();
                    saved++;
                }
                ps.executeBatch();
                conn.commit();
                String msg = "Saved " + saved + " mark(s)";
                if (errors > 0) msg += " (" + errors + " errors)";
                statusLabel.setText(msg);
                loadStudents(subjectId, selectedOutOf);
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
        if (!isTeacher) loadSubjects();
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

    private Map<String, Object[]> getSubjectsWithMarksCount() {
        Map<String, Object[]> map = new LinkedHashMap<>();
        int form = formBox.getValue();
        String stream = streamBox.getValue();
        String sql = """
            SELECT sub.id, sub.subject_code, sub.subject_name, sub.department,
                   COALESCE((SELECT es.out_of FROM exam_subjects es WHERE es.exam_id = ? AND es.subject_id = sub.id), 100) AS out_of,
                   (SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = sub.id) AS mark_count
            FROM subjects sub
            WHERE sub.id IN (SELECT ss.subject_id FROM stream_subjects ss WHERE ss.form = ? AND ss.stream = ?)
               OR NOT EXISTS (SELECT 1 FROM stream_subjects WHERE form = ? AND stream = ?)
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, selectedExamId);
            ps.setInt(3, form);
            ps.setString(4, stream);
            ps.setInt(5, form);
            ps.setString(6, stream);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                map.put(rs.getString("subject_name"), new Object[]{
                    rs.getLong("id"),
                    rs.getString("subject_code"),
                    rs.getString("department"),
                    rs.getInt("out_of"),
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

    private double getClassAverage(long examId, long subjectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT AVG(score) FROM marks WHERE exam_id = ? AND subject_id = ? AND score IS NOT NULL")) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) { return 0; }
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
        private String teacherComment;
        private String deviation;
        private boolean dirty;

        public StudentMarkRow(int pos, long studentId, String admission, String name,
                              Double score, String grade, Integer points,
                              String status, String teacherComment, String deviation) {
            this.pos = pos;
            this.studentId = studentId;
            this.admission = admission;
            this.name = name;
            this.score = score;
            this.grade = grade;
            this.points = points;
            this.status = status;
            this.teacherComment = teacherComment;
            this.deviation = deviation;
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
        public String getTeacherComment() { return teacherComment; }
        public String getDeviation() { return deviation; }
    }
}
