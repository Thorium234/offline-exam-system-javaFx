package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.IExamRepository;
import com.zaraki.exams.repository.IStudentRepository;
import com.zaraki.exams.repository.ISubjectRepository;
import com.zaraki.exams.repository.ITeacherSubjectRepository;
import com.zaraki.exams.repository.ExamRepositoryImpl;
import com.zaraki.exams.repository.StudentRepositoryImpl;
import com.zaraki.exams.repository.SubjectRepositoryImpl;
import com.zaraki.exams.repository.TeacherSubjectRepositoryImpl;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.service.ExamAnalysisServiceImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;

public class MarksEntryForm {

    private final DatabaseEngine db;
    private final IExamAnalysisService analysisService;
    private final IExamRepository examRepo;
    private final IStudentRepository studentRepo;
    private final ISubjectRepository subjectRepo;
    private final ITeacherSubjectRepository teacherSubjectRepo;
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
    private Label classInfoLabel;
    private TextField searchField;

    // Summary stats labels
    private Label statAvgValue, statHighestValue, statLowestValue, statCountValue;
    private Label subLabel;

    private long selectedExamId;
    private long selectedSubjectId;
    private final SimpleIntegerProperty selectedOutOf = new SimpleIntegerProperty(100);
    private String selectedSubjectName;

    private HBox teacherSubjectRow;
    private Button teacherLoadBtn;
    private VBox loadingOverlay;

    private final ObservableList<StudentMarkRow> masterData = FXCollections.observableArrayList();
    private FilteredList<StudentMarkRow> filteredData;

    private static final DecimalFormat SCORE_FMT = new DecimalFormat("#0.0");

    public MarksEntryForm(DatabaseEngine db, long loggedInUserId, String loggedInRole) {
        this.db = db;
        this.analysisService = new ExamAnalysisServiceImpl();
        this.examRepo = new ExamRepositoryImpl();
        this.studentRepo = new StudentRepositoryImpl();
        this.subjectRepo = new SubjectRepositoryImpl();
        this.teacherSubjectRepo = new TeacherSubjectRepositoryImpl();
        this.loggedInUserId = loggedInUserId;
        this.isTeacher = "teacher".equals(loggedInRole);
    }

    private VBox viewContainer;

    public VBox getView() {
        viewContainer = new VBox(12);
        viewContainer.getStyleClass().add("marks-entry-form");

        // ===== HEADER =====
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label header = new Label("Marks Entry");
        header.getStyleClass().add("page-header");
        headerRow.getChildren().add(header);
        headerRow.getChildren().add(createNotificationBadge());

        Label info = new Label(isTeacher
            ? "Select exam, subject, then stream. Grade & points auto-calculate."
            : "Select exam, class, and subject to enter marks. Grade & points auto-calculate.");
        info.getStyleClass().add("form-info");

        // ===== SELECTORS =====
        HBox selectorRow = new HBox(20);
        selectorRow.getStyleClass().add("selector-row");

        examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(260);
        loadExams();
        VBox examGroup = labeledField("Exam", examBox);

        teacherSubjectRow = new HBox(10);
        subjectBox = new ComboBox<>();
        subjectBox.setPromptText("Select Subject");
        subjectBox.setPrefWidth(220);
        teacherSubjectRow.getChildren().add(subjectBox);
        VBox subjectGroup = labeledField("Subject", teacherSubjectRow);

        HBox formStreamRow = new HBox(8);
        formBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Form");
        formBox.setPrefWidth(90);
        streamBox = new ComboBox<>();
        streamBox.setPromptText("Stream");
        streamBox.setPrefWidth(160);
        streamBox.setEditable(true);
        formStreamRow.getChildren().addAll(formBox, streamBox);

        teacherLoadBtn = new Button("Load Students");
        teacherLoadBtn.getStyleClass().addAll("button", "button-primary");
        teacherLoadBtn.setDisable(true);

        Button loadBtn = new Button("Load Subjects");
        loadBtn.getStyleClass().addAll("button", "button-primary");

        if (isTeacher) {
            formStreamRow.getChildren().add(teacherLoadBtn);
            selectorRow.getChildren().addAll(examGroup, subjectGroup, labeledField("Class", formStreamRow));
            setupTeacherActions();
        } else {
            formStreamRow.getChildren().add(loadBtn);
            selectorRow.getChildren().addAll(examGroup, labeledField("Class", formStreamRow));
            loadBtn.setOnAction(e -> loadSubjects());
        }

        // ===== SUBJECT CARDS =====
        subjectCardsArea = new FlowPane(12, 12);
        subjectCardsArea.setPadding(new Insets(5, 0, 5, 0));
        subjectCardsArea.setVisible(false);
        subjectCardsArea.getStyleClass().add("subject-cards");

        // ===== STUDENT ENTRY AREA =====
        studentEntryArea = new VBox(10);
        studentEntryArea.setVisible(false);
        studentEntryArea.getStyleClass().add("student-entry");

        // --- Student header bar (gradient banner) ---
        HBox studentHeader = new HBox(16);
        studentHeader.setAlignment(Pos.CENTER_LEFT);
        studentHeader.getStyleClass().add("subject-banner");
        selectedSubjectLabel = new Label();
        selectedSubjectLabel.getStyleClass().add("subject-banner-title");

        Button backBtn = new Button("← Back to Subjects");
        backBtn.getStyleClass().addAll("button");
        backBtn.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-background-radius: 6; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 6;");
        backBtn.setOnAction(e -> showSubjects());

        HBox headerLeft = new HBox(16);
        headerLeft.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerLeft, Priority.ALWAYS);
        subLabel = new Label();
        subLabel.getStyleClass().add("subject-banner-sub");
        headerLeft.getChildren().addAll(selectedSubjectLabel, subLabel);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search by name or admission...");
        searchField.setPrefWidth(240);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, val) -> {
            if (filteredData != null) {
                filteredData.setPredicate(row -> {
                    if (val == null || val.isBlank()) return true;
                    String q = val.toLowerCase();
                    return row.getAdmission().toLowerCase().contains(q)
                        || row.getName().toLowerCase().contains(q);
                });
            }
        });

        studentHeader.getChildren().addAll(headerLeft, searchField, backBtn);

        // --- Summary Stats Bar ---
        HBox statsBar = new HBox(0);
        statsBar.getStyleClass().add("stats-bar");
        statAvgValue = new Label("—");
        statHighestValue = new Label("—");
        statLowestValue = new Label("—");
        statCountValue = new Label("—");
        statsBar.getChildren().addAll(
            statTile("📊  Class Average", statAvgValue),
            statTile("⬆  Highest", statHighestValue),
            statTile("⬇  Lowest", statLowestValue),
            statTile("👥  Students", statCountValue)
        );

        // --- Table ---
        studentTable = new TableView<>();
        studentTable.setEditable(true);
        studentTable.setFixedCellSize(42);
        studentTable.setMinHeight(360);
        studentTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        studentTable.getStyleClass().add("marks-table");
        VBox.setVgrow(studentTable, Priority.ALWAYS);
        setupTableColumns();

        // --- Bottom bar ---
        HBox bottomBar = new HBox(12);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        saveAllBtn = new Button("💾  Save All Marks");
        saveAllBtn.getStyleClass().addAll("button", "button-success", "button-lg");

        Button refreshBtn = new Button("🔄  Refresh");
        refreshBtn.getStyleClass().addAll("button", "button-secondary");
        refreshBtn.setOnAction(e -> {
            if (selectedSubjectId > 0) loadStudents(selectedSubjectId, selectedOutOf.get());
        });

        classInfoLabel = new Label();
        classInfoLabel.getStyleClass().add("status-label");
        HBox.setHgrow(classInfoLabel, Priority.ALWAYS);
        classInfoLabel.setAlignment(Pos.CENTER_RIGHT);

        bottomBar.getChildren().addAll(saveAllBtn, refreshBtn, classInfoLabel);

        VBox studentCard = new VBox(0);
        studentCard.getChildren().addAll(studentHeader, studentEntryArea);
        VBox.setVgrow(studentEntryArea, Priority.ALWAYS);
        studentEntryArea.getChildren().addAll(statsBar, studentTable, bottomBar);
        studentEntryArea.setMinHeight(0);

        // ===== LOADING OVERLAY =====
        loadingOverlay = new VBox(10);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.getStyleClass().add("loading-overlay");
        loadingOverlay.setVisible(false);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(40, 40);
        Label loadingLbl = new Label("Loading...");
        loadingLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        loadingLbl.setTextFill(Color.web("#1E40AF"));
        loadingOverlay.getChildren().addAll(spinner, loadingLbl);

        StackPane contentStack = new StackPane();
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        VBox contentBox = new VBox(12, selectorRow, subjectCardsArea, studentCard);
        VBox.setVgrow(studentCard, Priority.ALWAYS);
        contentStack.getChildren().addAll(contentBox, loadingOverlay);

        saveAllBtn.setOnAction(e -> saveAllMarks());
        studentTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.TAB && studentTable.getEditingCell() != null) {
                int row = studentTable.getEditingCell().getRow();
                if (row + 1 < studentTable.getItems().size()) {
                    studentTable.edit(row + 1, studentTable.getColumns().get(3));
                    e.consume();
                }
            }
        });

        HBox.setHgrow(contentStack, Priority.ALWAYS);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        viewContainer.getChildren().addAll(headerRow, info, contentStack);
        return viewContainer;
    }

    // ─────────────────────────────────────────────
    //  TABLE SETUP
    // ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        TableColumn<StudentMarkRow, Integer> colPos = new TableColumn<>("#");
        colPos.setCellValueFactory(new PropertyValueFactory<>("pos"));
        colPos.setPrefWidth(40);
        colPos.setMinWidth(36);
        colPos.setMaxWidth(50);
        colPos.setResizable(false);
        colPos.getStyleClass().add("col-pos");

        TableColumn<StudentMarkRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        colAdm.setPrefWidth(115);
        colAdm.setMinWidth(90);
        colAdm.getStyleClass().add("col-adm");

        TableColumn<StudentMarkRow, String> colName = new TableColumn<>("Student Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(190);
        colName.setMinWidth(120);
        colName.getStyleClass().add("col-name");

        double outOf = selectedOutOf.get() > 0 ? selectedOutOf.get() : 100;
        TableColumn<StudentMarkRow, Double> colScore = new TableColumn<>("Score / " + outOf);
        colScore.setPrefWidth(105);
        colScore.setMinWidth(90);
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colScore.setEditable(true);
        colScore.setCellFactory(col -> new ScoreCell(this::onScoreEdit));
        colScore.setOnEditCommit(e -> { /* handled by ScoreCell */ });
        colScore.getStyleClass().add("col-score");

        TableColumn<StudentMarkRow, String> colGrade = new TableColumn<>("Grade");
        colGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));
        colGrade.setPrefWidth(60);
        colGrade.setMinWidth(50);
        colGrade.setMaxWidth(75);
        colGrade.setCellFactory(col -> new GradeCell());
        colGrade.getStyleClass().add("col-grade");

        TableColumn<StudentMarkRow, Integer> colPoints = new TableColumn<>("Pts");
        colPoints.setCellValueFactory(new PropertyValueFactory<>("points"));
        colPoints.setPrefWidth(50);
        colPoints.setMinWidth(45);
        colPoints.setMaxWidth(65);
        colPoints.getStyleClass().add("col-points");

        TableColumn<StudentMarkRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));
        colStatus.setPrefWidth(85);
        colStatus.setMinWidth(75);
        colStatus.setCellFactory(col -> new StatusCell());
        colStatus.getStyleClass().add("col-status");

        TableColumn<StudentMarkRow, String> colDeviation = new TableColumn<>("Deviation");
        colDeviation.setCellValueFactory(new PropertyValueFactory<>("deviation"));
        colDeviation.setPrefWidth(85);
        colDeviation.setMinWidth(75);
        colDeviation.setCellFactory(col -> new DeviationCell());
        colDeviation.getStyleClass().add("col-deviation");

        TableColumn<StudentMarkRow, String> colComment = new TableColumn<>("Comment");
        colComment.setCellValueFactory(new PropertyValueFactory<>("teacherComment"));
        colComment.setPrefWidth(150);
        colComment.setMinWidth(100);
        colComment.setEditable(true);
        colComment.setCellFactory(col -> new CommentCell());
        colComment.getStyleClass().add("col-comment");

        TableColumn<StudentMarkRow, Void> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(80);
        colAction.setCellFactory(col -> new ActionCell(this::saveSingleRow));
        colAction.getStyleClass().add("col-action");
        colAction.setSortable(false);

        studentTable.getColumns().addAll(colPos, colAdm, colName, colScore, colGrade,
            colPoints, colStatus, colDeviation, colComment, colAction);

        // Dynamically update score header when outOf loads
        colScore.textProperty().bind(
            new SimpleStringProperty("Score / ").concat(selectedOutOf.asString())
        );
    }

    // ─────────────────────────────────────────────
    //  SCORE CELL (editable TextField with validation)
    // ─────────────────────────────────────────────

    private class ScoreCell extends TableCell<StudentMarkRow, Double> {
        private final TextField field = new TextField();
        private final PauseTransition commitDelay = new PauseTransition(Duration.millis(600));
        private boolean committing;
        private final java.util.function.BiConsumer<StudentMarkRow, Double> onCommit;

        ScoreCell(java.util.function.BiConsumer<StudentMarkRow, Double> onCommit) {
            this.onCommit = onCommit;
            field.setPrefWidth(80);
            field.getStyleClass().add("score-field");
            field.textProperty().addListener((obs, old, val) -> {
                if (committing) return;
                committing = true;
                commitDelay.setOnFinished(e -> processInput(val));
                commitDelay.playFromStart();
                committing = false;
            });
            field.focusedProperty().addListener((obs, was, now) -> {
                if (!now) processInput(field.getText());
            });
            // Tab key → move to next row
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.TAB) {
                    processInput(field.getText());
                    int idx = getIndex();
                    if (idx + 1 < getTableView().getItems().size()) {
                        getTableView().edit(idx + 1, getTableColumn());
                    }
                    e.consume();
                } else if (e.getCode() == KeyCode.ENTER) {
                    processInput(field.getText());
                    e.consume();
                }
            });
        }

        private void processInput(String text) {
            if (text == null || text.isBlank()) {
                StudentMarkRow row = getTableRow().getItem();
                if (row != null) { row.score = null; row.grade = null; row.points = null; row.dirty = true; recalcStats(); }
                return;
            }
            try {
                double val = Double.parseDouble(text);
                if (val >= 0 && val <= selectedOutOf.get()) {
                    StudentMarkRow row = getTableRow().getItem();
                    if (row != null) {
                        double normalized = analysisService.normalizeByOutOf(val, selectedSubjectId, selectedExamId);
                        onCommit.accept(row, normalized);
                        row.dirty = true;
                        recalcStats();
                        getTableView().refresh();
                    }
                } else {
                    UIUtils.showError("Score must be between 0 and " + selectedOutOf.get());
                }
            } catch (NumberFormatException ex) {
                UIUtils.showError("Enter a valid number.");
            }
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                Double val = getTableRow().getItem().score;
                field.setText(val != null ? SCORE_FMT.format(val) : "");
                field.setPromptText("0-" + selectedOutOf.get());
                setGraphic(field);
            }
        }
    }

    private void onScoreEdit(StudentMarkRow row, double normalizedScore) {
        row.score = normalizedScore;
        String result = analysisService.determineGradeAndPoints(normalizedScore, selectedSubjectId, selectedExamId);
        String[] parts = result.split("\\|");
        row.grade = parts.length > 0 ? parts[0] : "";
        row.points = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    // ─────────────────────────────────────────────
    //  GRADE CELL (color-coded A=green through E=red)
    // ─────────────────────────────────────────────

    private static class GradeCell extends TableCell<StudentMarkRow, String> {
        private final Label lbl = new Label();
        { lbl.setAlignment(Pos.CENTER); lbl.setPrefWidth(60); }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                String grade = getTableRow().getItem().grade;
                lbl.setText(grade != null ? grade : "");
                lbl.getStyleClass().removeAll(
                    "grade-A", "grade-A-", "grade-B", "grade-B-",
                    "grade-C", "grade-C-", "grade-D", "grade-D-", "grade-E");
                if (grade != null && !grade.isEmpty()) {
                    String cssClass = "grade-" + grade.replace("+", "").replace(" ", "");
                    lbl.getStyleClass().add(cssClass);
                }
                setGraphic(lbl);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  STATUS CELL (auto-calculated with badge)
    // ─────────────────────────────────────────────

    private static class StatusCell extends TableCell<StudentMarkRow, String> {
        private final Label badge = new Label();
        { badge.setPrefWidth(70); badge.setAlignment(Pos.CENTER); }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                StudentMarkRow row = getTableRow().getItem();
                String status = deriveStatus(row);
                badge.setText(status);
                badge.getStyleClass().removeAll("badge-present", "badge-absent", "badge-defaulter", "badge-pending");
                switch (status) {
                    case "Present" -> badge.getStyleClass().add("badge-present");
                    case "Absent" -> badge.getStyleClass().add("badge-absent");
                    case "Defaulter" -> badge.getStyleClass().add("badge-defaulter");
                    default -> badge.getStyleClass().add("badge-pending");
                }
                setGraphic(badge);
            }
        }

        private String deriveStatus(StudentMarkRow row) {
            String s = row.status;
            if ("A".equalsIgnoreCase(s)) return "Absent";
            if ("D".equalsIgnoreCase(s)) return "Defaulter";
            if (row.score != null && row.grade != null && !row.grade.isEmpty()) return "Present";
            if ("P".equalsIgnoreCase(s)) return "Pending";
            return "Pending";
        }
    }

    // ─────────────────────────────────────────────
    //  DEVIATION CELL (color-coded)
    // ─────────────────────────────────────────────

    private static class DeviationCell extends TableCell<StudentMarkRow, String> {
        private final Label lbl = new Label();
        { lbl.setAlignment(Pos.CENTER); lbl.setPrefWidth(80); }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                StudentMarkRow row = getTableRow().getItem();
                if (row.score == null) {
                    setGraphic(null);
                    return;
                }
                // Compute deviation vs class average
                double avg = computeLocalAvg();
                double dev = avg > 0 ? Math.round((row.score - avg) * 10.0) / 10.0 : 0;
                lbl.setText((dev >= 0 ? "+" : "") + SCORE_FMT.format(dev));
                lbl.setTextFill(dev >= 0 ? Color.web("#2e7d32") : Color.web("#c62828"));
                lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
                setGraphic(lbl);
            }
        }

        private double computeLocalAvg() {
            TableView<StudentMarkRow> tv = getTableView();
            if (tv == null) return 0;
            double sum = 0, count = 0;
            for (StudentMarkRow r : tv.getItems()) {
                if (r.score != null) { sum += r.score; count++; }
            }
            return count > 0 ? sum / count : 0;
        }
    }

    // ─────────────────────────────────────────────
    //  COMMENT CELL (tooltip on hover, expandable)
    // ─────────────────────────────────────────────

    private static class CommentCell extends TableCell<StudentMarkRow, String> {
        private final TextField field = new TextField();
        private final PauseTransition delay = new PauseTransition(Duration.millis(400));

        CommentCell() {
            field.setPrefWidth(130);
            field.setPromptText("Add comment...");
            field.textProperty().addListener((obs, old, val) -> {
                delay.setOnFinished(e -> {
                    StudentMarkRow row = getTableRow().getItem();
                    if (row != null) { row.teacherComment = val; row.dirty = true; }
                });
                delay.playFromStart();
            });
            field.focusedProperty().addListener((obs, was, now) -> {
                if (!now) {
                    StudentMarkRow row = getTableRow().getItem();
                    if (row != null) { row.teacherComment = field.getText(); row.dirty = true; }
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                field.setText(getTableRow().getItem().teacherComment != null ? getTableRow().getItem().teacherComment : "");
                setGraphic(field);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  ACTION CELL (Save Row button)
    // ─────────────────────────────────────────────

    private class ActionCell extends TableCell<StudentMarkRow, Void> {
        private final Button saveBtn = new Button("💾");
        private final java.util.function.Consumer<StudentMarkRow> onSave;

        ActionCell(java.util.function.Consumer<StudentMarkRow> onSave) {
            this.onSave = onSave;
            saveBtn.setPrefWidth(60);
            saveBtn.getStyleClass().addAll("button", "button-success", "btn-sm");
            saveBtn.setTooltip(new Tooltip("Save this row"));
            saveBtn.setOnAction(e -> {
                StudentMarkRow row = getTableRow().getItem();
                if (row != null) onSave.accept(row);
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(empty ? null : saveBtn);
        }
    }

    // ─────────────────────────────────────────────
    //  SAVE SINGLE ROW
    // ─────────────────────────────────────────────

    private void saveSingleRow(StudentMarkRow row) {
        if (selectedExamId == 0 || selectedSubjectId == 0) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved, status, teacher_comment, deviation) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, row.studentId);
            ps.setLong(3, selectedSubjectId);
            if (row.score != null) ps.setDouble(4, row.score);
            else ps.setNull(4, java.sql.Types.REAL);
            ps.setString(5, row.grade);
            if (row.points != null) ps.setInt(6, row.points);
            else ps.setNull(6, java.sql.Types.INTEGER);
            ps.setString(7, row.status != null && !row.status.isEmpty() ? row.status : "P");
            ps.setString(8, row.teacherComment);
            ps.setDouble(9, row.score != null ? computeSingleDeviation(row.studentId) : 0);
            ps.executeUpdate();
            row.dirty = false;
            showToast(row.getName() + " saved ✅", "success");
        } catch (SQLException ex) {
            UIUtils.showError("Failed to save: " + ex.getMessage());
        }
    }

    private double computeSingleDeviation(long studentId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT AVG(score) FROM marks WHERE exam_id = ? AND subject_id = ? AND score IS NOT NULL")) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, selectedSubjectId);
            ResultSet rs = ps.executeQuery();
            double avg = rs.next() ? rs.getDouble(1) : 0;
            return avg > 0 ? Math.round((getStudentScore(studentId) - avg) * 10.0) / 10.0 : 0;
        } catch (SQLException e) { return 0; }
    }

    private double getStudentScore(long studentId) {
        for (StudentMarkRow r : masterData) {
            if (r.studentId == studentId && r.score != null) return r.score;
        }
        return 0;
    }

    // ─────────────────────────────────────────────
    //  TOAST NOTIFICATION
    // ─────────────────────────────────────────────

    private final VBox toastContainer = new VBox(8);
    {
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPadding(new Insets(10));
        toastContainer.setPickOnBounds(false);
        toastContainer.setMouseTransparent(true);
    }

    private Label notificationBadge;

    private Label createNotificationBadge() {
        notificationBadge = new Label();
        notificationBadge.setVisible(false);
        return notificationBadge;
    }

    private void showToast(String message, String type) {
        Label toast = new Label(message);
        toast.setPadding(new Insets(10, 18, 10, 18));
        toast.setStyle(
            "-fx-background-color: " + ("success".equals(type) ? "#2e7d32" : "#1a237e") + "; "
            + "-fx-text-fill: white; -fx-background-radius: 8; "
            + "-fx-font-size: 13px; -fx-font-weight: bold; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);"
        );
        toast.setOpacity(0);
        toast.setTranslateX(50);
        toastContainer.getChildren().add(toast);

        PauseTransition fadeIn = new PauseTransition(Duration.millis(100));
        fadeIn.setOnFinished(e -> {
            toast.setOpacity(1);
            toast.setTranslateX(0);
        });
        fadeIn.play();

        PauseTransition dismiss = new PauseTransition(Duration.seconds(3));
        dismiss.setOnFinished(e -> {
            toast.setOpacity(0);
            toast.setTranslateX(50);
            PauseTransition remove = new PauseTransition(Duration.millis(300));
            remove.setOnFinished(x -> toastContainer.getChildren().remove(toast));
            remove.play();
        });
        dismiss.play();
    }

    // ─────────────────────────────────────────────
    //  STATS BAR HELPERS
    // ─────────────────────────────────────────────

    private VBox statTile(String label, Label valueLabel) {
        VBox tile = new VBox(2);
        tile.setAlignment(Pos.CENTER);
        tile.setPrefWidth(140);
        tile.setPadding(new Insets(8, 12, 8, 12));
        tile.getStyleClass().add("stat-tile");
        valueLabel.getStyleClass().add("stat-tile-value");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-tile-label");
        tile.getChildren().addAll(valueLabel, lbl);
        return tile;
    }

    private VBox labeledField(String labelText, Node field) {
        VBox box = new VBox(4);
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.gray(0.5));
        box.getChildren().addAll(lbl, field);
        return box;
    }

    // ─────────────────────────────────────────────
    //  RECALCULATE STATS
    // ─────────────────────────────────────────────

    private void recalcStats() {
        double sum = 0, highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
        int scoredCount = 0, total = studentTable.getItems().size();
        for (StudentMarkRow r : studentTable.getItems()) {
            if (r.score != null) {
                sum += r.score;
                scoredCount++;
                if (r.score > highest) highest = r.score;
                if (r.score < lowest) lowest = r.score;
            }
        }
        double avg = scoredCount > 0 ? sum / scoredCount : 0;
        statAvgValue.setText(scoredCount > 0 ? SCORE_FMT.format(avg) : "—");
        statHighestValue.setText(scoredCount > 0 ? SCORE_FMT.format(highest) : "—");
        statLowestValue.setText(scoredCount > 0 ? SCORE_FMT.format(lowest) : "—");
        statCountValue.setText(String.valueOf(total));
    }

    // ─────────────────────────────────────────────
    //  LOADING STATE
    // ─────────────────────────────────────────────

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        studentTable.setDisable(show);
        saveAllBtn.setDisable(show);
    }

    // ─────────────────────────────────────────────
    //  TEACHER ACTIONS
    // ─────────────────────────────────────────────

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
        try {
            var subjects = subjectRepo.findByTeacher(loggedInUserId);
            for (var s : subjects)
                subjectBox.getItems().add(s.get("id") + ":" + s.get("subject_code") + " - " + s.get("subject_name"));
        } catch (Exception ex) { UIUtils.showError("Failed to load subjects: " + ex.getMessage()); }
    }

    private void loadTeacherForms() {
        formBox.setValue(null);
        streamBox.getItems().clear();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
        selectedSubjectId = subjectId;
        selectedSubjectName = subjectBox.getValue().split(" - ", 2)[1];
        var forms = teacherSubjectRepo.findFormsByTeacherAndSubject(loggedInUserId, subjectId);
        formBox.setItems(FXCollections.observableArrayList(forms));
    }

    private void loadTeacherStreams() {
        streamBox.getItems().clear();
        int form = formBox.getValue();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
        var streams = teacherSubjectRepo.findStreamsByTeacherAndSubjectAndForm(loggedInUserId, subjectId, form);
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
        if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
        if (subjectBox.getValue() == null) { UIUtils.showError("Select a subject."); return; }
        if (formBox.getValue() == null) { UIUtils.showError("Select a form."); return; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) { UIUtils.showError("Select a stream."); return; }
        selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        selectedSubjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
        int outOf = getOutOf(selectedExamId, selectedSubjectId);
        if (outOf <= 0) outOf = 100;
        selectedOutOf.set(outOf);
        loadStudents(selectedSubjectId, selectedOutOf.get());
    }

    // ─────────────────────────────────────────────
    //  SUBJECT LOADING
    // ─────────────────────────────────────────────

    private void loadSubjects() {
        if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
        if (formBox.getValue() == null) { UIUtils.showError("Select a form."); return; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) { UIUtils.showError("Select a stream."); return; }

        selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        studentEntryArea.setVisible(false);
        subjectCardsArea.getChildren().clear();

        int form = formBox.getValue();
        String stream = streamBox.getValue();
        int studentCount = studentRepo.countByFormStream(form, stream);
        if (studentCount == 0) {
            UIUtils.showError("No students found in Form " + form + " - " + stream);
            return;
        }

        var subjects = subjectRepo.findByFormStreamWithMarksCount(selectedExamId, form, stream);
        if (subjects.isEmpty()) {
            UIUtils.showError("No subjects defined. Add subjects first.");
            return;
        }

        for (var entry : subjects) {
            String name = (String) entry.get("subject_name");
            long subjId = (Long) entry.get("id");
            String code = (String) entry.get("subject_code");
            String dept = (String) entry.get("department");
            int outOf = (Integer) entry.get("out_of");
            int markCount = (Integer) entry.get("mark_count");

            VBox card = new VBox(6);
            card.setPrefSize(170, 100);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(12));
            card.getStyleClass().add("subject-card");

            Label nameLbl = new Label(name);
            nameLbl.getStyleClass().add("subject-card-name");
            nameLbl.setWrapText(true);
            nameLbl.setAlignment(Pos.CENTER);

            Label codeLbl = new Label(code + " | " + dept);
            codeLbl.getStyleClass().add("subject-card-detail");

            Label countLbl = new Label(markCount + " marks entered");
            countLbl.getStyleClass().add("subject-card-count");
            countLbl.setTextFill(markCount > 0 ? Color.web("#2e7d32") : Color.gray(0.5));

            card.getChildren().addAll(nameLbl, codeLbl, countLbl);
            if (markCount > 0) card.getStyleClass().add("has-marks");

            long sid = subjId;
            int oo = outOf;
            card.setOnMouseClicked(e -> loadStudents(sid, oo));
            subjectCardsArea.getChildren().add(card);
        }

        subjectCardsArea.setVisible(true);
        classInfoLabel.setText("📚 Form " + form + " - " + stream + "  |  👥 " + studentCount + " students");
    }

    // ─────────────────────────────────────────────
    //  STUDENT LOADING
    // ─────────────────────────────────────────────

    private void loadStudents(long subjectId, int outOf) {
        showLoading(true);
        selectedSubjectId = subjectId;
        selectedOutOf.set(outOf);
        selectedSubjectName = subjectRepo.getName(subjectId);
        selectedSubjectLabel.setText("📖 " + selectedSubjectName);
        studentEntryArea.setVisible(true);
        studentEntryArea.setManaged(true);
        subjectCardsArea.setVisible(false);
        subjectCardsArea.setManaged(false);
        masterData.clear();

        int form = formBox.getValue();
        String stream = streamBox.getValue();
        subLabel.setText("Form " + form + " — " + stream + "  ·  " + selectedOutOf.get() + " marks max");

        double classAvg = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT AVG(score) FROM marks WHERE exam_id = ? AND subject_id = ?")) {
            ps.setLong(1, selectedExamId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) classAvg = rs.getDouble(1);
        } catch (SQLException e) { /* ignore */ }

        try {
            var students = studentRepo.findByFormStreamWithMarks(selectedExamId, subjectId, form, stream);
            int pos = 0;
            double finalClassAvg = classAvg;
            for (var s : students) {
                pos++;
                Double scoreVal = s.get("score") != null ? ((Number) s.get("score")).doubleValue() : null;
                String savedStatus = (String) s.get("status");
                String status = savedStatus != null ? savedStatus : (scoreVal != null ? "P" : "");
                String comment = (String) s.get("teacher_comment");
                int pts = s.get("points_achieved") != null ? ((Number) s.get("points_achieved")).intValue() : 0;
                double dev = finalClassAvg > 0 && scoreVal != null
                    ? Math.round((scoreVal - finalClassAvg) * 10.0) / 10.0 : 0;
                masterData.add(new StudentMarkRow(
                    pos,
                    (Long) s.get("id"),
                    (String) s.get("admission_number"),
                    (String) s.get("full_name"),
                    scoreVal,
                    (String) s.get("grade_achieved"),
                    pts,
                    status,
                    comment != null ? comment : "",
                    dev != 0 ? (dev > 0 ? "+" : "") + SCORE_FMT.format(dev) : ""
                ));
            }
        } catch (Exception e) { UIUtils.showError(e.getMessage()); }

        filteredData = new FilteredList<>(masterData, p -> true);
        studentTable.setItems(filteredData);
        // Force layout pass after visibility transition
        Platform.runLater(() -> studentTable.requestLayout());
        recalcStats();
        classInfoLabel.setText("📖 " + selectedSubjectName + "  |  👥 " + masterData.size() + " students");
        showLoading(false);
        if (masterData.isEmpty()) showEmptyState();
    }

    // ─────────────────────────────────────────────
    //  EMPTY STATE
    // ─────────────────────────────────────────────

    private void showEmptyState() {
        studentTable.setPlaceholder(new EmptyStatePlaceholder(
            "No students found for this class.\nAdd students or select a different class.", "👥").getView());
    }

    // ─────────────────────────────────────────────
    //  SAVE ALL MARKS
    // ─────────────────────────────────────────────

    private void saveAllMarks() {
        long examId = selectedExamId;
        long subjectId = selectedSubjectId;
        if (examId == 0 || subjectId == 0) return;
        saveAllBtn.setText("💾  Saving...");
        saveAllBtn.setDisable(true);

        List<StudentMarkRow> dirtyRows = new ArrayList<>();
        for (StudentMarkRow row : studentTable.getItems()) {
            if (row.dirty) dirtyRows.add(row);
        }
        if (dirtyRows.isEmpty()) {
            showToast("No changes to save", "info");
            saveAllBtn.setText("💾  Save All Marks");
            saveAllBtn.setDisable(false);
            return;
        }

        int saved = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved, status, teacher_comment, deviation) VALUES (?,?,?,?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            try {
                for (StudentMarkRow row : dirtyRows) {
                    String status = (row.status != null && !row.status.isEmpty()) ? row.status : "P";
                    double deviation = 0;
                    if (row.score != null) {
                        double avg = getClassAverage(examId, subjectId);
                        deviation = Math.round((row.score - avg) * 10.0) / 10.0;
                    }
                    ps.setLong(1, examId);
                    ps.setLong(2, row.studentId);
                    ps.setLong(3, subjectId);
                    if (row.score != null) ps.setDouble(4, row.score);
                    else ps.setNull(4, java.sql.Types.REAL);
                    ps.setString(5, row.grade);
                    if (row.points != null) ps.setInt(6, row.points);
                    else ps.setNull(6, java.sql.Types.INTEGER);
                    ps.setString(7, status);
                    ps.setString(8, row.teacherComment);
                    ps.setDouble(9, deviation);
                    ps.addBatch();
                    saved++;
                }
                ps.executeBatch();
                conn.commit();
                for (StudentMarkRow r : dirtyRows) r.dirty = false;
                showToast(saved + " mark(s) saved successfully ✅", "success");
            } catch (Exception e) {
                conn.rollback();
                UIUtils.showError("Failed to save: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            UIUtils.showError("DB error: " + e.getMessage());
        }
        saveAllBtn.setText("💾  Save All Marks");
        saveAllBtn.setDisable(false);
        loadStudents(selectedSubjectId, selectedOutOf.get());
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private void showSubjects() {
        studentEntryArea.setVisible(false);
        studentEntryArea.setManaged(false);
        subjectCardsArea.setVisible(true);
        subjectCardsArea.setManaged(true);
        if (!isTeacher) loadSubjects();
    }

    private void loadExams() {
        try {
            var exams = examRepo.findAllDesc();
            for (var e : exams)
                examBox.getItems().add(e.get("id") + " - " + e.get("academic_year")
                    + " " + e.get("term") + " " + e.get("exam_series"));
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
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

    // ─────────────────────────────────────────────
    //  STUDENT MARK ROW MODEL
    // ─────────────────────────────────────────────

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

        // Computed property for status display
        public String getStatusDisplay() {
            if ("A".equalsIgnoreCase(status)) return "Absent";
            if ("D".equalsIgnoreCase(status)) return "Defaulter";
            if (score != null && grade != null) return "Entered";
            if ("P".equalsIgnoreCase(status)) return "Pending";
            return "Pending";
        }
    }
}
