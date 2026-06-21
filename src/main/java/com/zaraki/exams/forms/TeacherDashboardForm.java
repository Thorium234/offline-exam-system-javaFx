package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import static com.zaraki.exams.forms.AppTheme.PRIMARY;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import javafx.util.converter.DoubleStringConverter;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class TeacherDashboardForm {

    private static final String CARD_STYLE = "-fx-background-color: white; -fx-background-radius: 10; "
        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);";

    private final DatabaseEngine db;
    private final String loggedInUser;
    private final String loggedInUsername;
    private final long userId;
    private final Runnable onLogout;
    private final StackPane root;
    private final ExamAnalysisService analysisService = new ExamAnalysisService();

    private long selectedExamId;
    private long selectedSubjectId;
    private int selectedOutOf = 100;

    public TeacherDashboardForm(DatabaseEngine db, String loggedInUser, String loggedInUsername,
                                 long userId, Runnable onLogout) {
        this.db = db;
        this.loggedInUser = loggedInUser;
        this.loggedInUsername = loggedInUsername;
        this.userId = userId;
        this.onLogout = onLogout;
        this.root = new StackPane();
        showOverview();
    }

    public Node getView() { return root; }
    private void setView(Node node) { root.getChildren().setAll(node); }

    // ───── Overview: My Classes ─────

    private void showOverview() {
        VBox view = new VBox(20);

        Label header = UIUtils.makeHeader("My Dashboard");

        Label roleBadge = new Label("Logged in as " + loggedInUser + " (Teacher)");
        roleBadge.setFont(Font.font("System", 13));
        roleBadge.setTextFill(Color.gray(0.5));

        Label classHeader = new Label("My Classes");
        classHeader.setFont(Font.font("System", FontWeight.BOLD, 18));

        FlowPane classCards = new FlowPane(15, 15);
        classCards.setPadding(new Insets(5, 0, 5, 0));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(24, 24);

        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");
        logoutBtn.setOnAction(e -> { if (onLogout != null) onLogout.run(); });

        Task<List<ClassInfo>> loadTask = new Task<>() {
            @Override protected List<ClassInfo> call() {
                List<ClassInfo> classes = new ArrayList<>();
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT ts.form, ts.stream, GROUP_CONCAT(s.subject_name, '||') AS subjects, "
                         + "GROUP_CONCAT(s.id, '||') AS subject_ids "
                         + "FROM teacher_subjects ts "
                         + "JOIN subjects s ON s.id = ts.subject_id "
                         + "WHERE ts.user_id = ? "
                         + "GROUP BY ts.form, ts.stream "
                         + "ORDER BY ts.form, ts.stream")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int form = rs.getInt("form");
                            String stream = rs.getString("stream");
                            String subjectsRaw = rs.getString("subjects");
                            String subjectIdsRaw = rs.getString("subject_ids");
                            List<String> subjects = subjectsRaw != null
                                ? Arrays.asList(subjectsRaw.split("\\|\\|")) : List.of();
                            List<Long> subjectIds = subjectIdsRaw != null
                                ? Arrays.stream(subjectIdsRaw.split("\\|\\|")).map(Long::parseLong).collect(Collectors.toList())
                                : List.of();
                            classes.add(new ClassInfo(form, stream, subjects, subjectIds));
                        }
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }
                return classes;
            }
        };
        loadTask.setOnSucceeded(ev -> {
            List<ClassInfo> classes = loadTask.getValue();
            for (ClassInfo ci : classes) {
                VBox card = new VBox(5);
                card.setPrefSize(220, 120);
                card.setAlignment(Pos.CENTER);
                card.setStyle(CARD_STYLE);

                Label formLabel = new Label("Form " + ci.form + " - " + ci.stream);
                formLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
                formLabel.setTextFill(Color.web(PRIMARY));

                Label subjLabel = new Label(String.join(", ", ci.subjects));
                subjLabel.setFont(Font.font("System", 11));
                subjLabel.setTextFill(Color.gray(0.5));
                subjLabel.setWrapText(true);
                subjLabel.setAlignment(Pos.CENTER);

                int f = ci.form; String s = ci.stream;
                card.setOnMouseEntered(e -> card.setStyle(
                    "-fx-background-color: #e8eaf6; -fx-background-radius: 10; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"));
                card.setOnMouseExited(e -> card.setStyle(CARD_STYLE));
                card.setOnMouseClicked(e -> showMarksEntry(f, s));
                card.setCursor(javafx.scene.Cursor.HAND);

                card.getChildren().addAll(formLabel, subjLabel);
                classCards.getChildren().add(card);
            }
            spinner.setVisible(false);
            if (classes.isEmpty()) {
                classCards.getChildren().add(new Label("No classes allocated yet."));
            }
        });
        loadTask.setOnFailed(ev -> {
            spinner.setVisible(false);
            UIUtils.showError("Failed to load: " + loadTask.getException().getMessage());
        });
        new Thread(loadTask).start();

        view.getChildren().addAll(header, roleBadge, classHeader, spinner, classCards, logoutBtn);
        setView(view);
    }

    // ───── Marks Entry per Class ─────

    private void showMarksEntry(int form, String stream) {
        VBox view = new VBox(15);

        Button backBtn = new Button("← Back to My Classes");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY + "; -fx-font-size: 13;");
        backBtn.setOnAction(e -> showOverview());

        Label header = new Label("Marks Entry — Form " + form + " " + stream);
        header.setFont(Font.font("System", FontWeight.BOLD, 20));

        HBox examRow = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        loadExams(examBox);
        examRow.getChildren().addAll(new Label("Exam:"), examBox);

        HBox subjectRow = new HBox(10);
        ComboBox<String> subjectBox = new ComboBox<>();
        subjectBox.setPromptText("Select Subject");
        subjectBox.setPrefWidth(250);
        subjectRow.getChildren().addAll(new Label("Subject:"), subjectBox);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(24, 24);
        spinner.setVisible(false);

        HBox actions = new HBox(10);
        Button loadBtn = new Button("Load Students");
        Button saveBtn = new Button("Save Marks");
        saveBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        Label statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setTextFill(Color.gray(0.5));
        actions.getChildren().addAll(loadBtn, saveBtn, spinner, statusLabel);

        TableView<MarkRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(true);

        TableColumn<MarkRow, Integer> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("pos"));
        cPos.setPrefWidth(35);

        TableColumn<MarkRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        cAdm.setPrefWidth(120);

        TableColumn<MarkRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("name"));
        cName.setPrefWidth(200);

        TableColumn<MarkRow, Double> cScore = new TableColumn<>("Score");
        cScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        cScore.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        cScore.setPrefWidth(100);

        TableColumn<MarkRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));
        cGrade.setPrefWidth(60);

        TableColumn<MarkRow, Integer> cPts = new TableColumn<>("Pts");
        cPts.setCellValueFactory(new PropertyValueFactory<>("points"));
        cPts.setPrefWidth(50);

        table.getColumns().addAll(cPos, cAdm, cName, cScore, cGrade, cPts);
        ObservableList<MarkRow> data = FXCollections.observableArrayList();

        // Load teacher's subjects for this class
        Task<List<SubjectInfo>> loadSubjTask = new Task<>() {
            @Override protected List<SubjectInfo> call() {
                List<SubjectInfo> list = new ArrayList<>();
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT s.id, s.subject_name, s.subject_code "
                         + "FROM teacher_subjects ts JOIN subjects s ON s.id = ts.subject_id "
                         + "WHERE ts.user_id = ? AND ts.form = ? AND ts.stream = ? "
                         + "ORDER BY s.subject_name")) {
                    ps.setLong(1, userId);
                    ps.setInt(2, form);
                    ps.setString(3, stream);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next())
                            list.add(new SubjectInfo(rs.getLong("id"),
                                rs.getString("subject_name"), rs.getString("subject_code")));
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }
                return list;
            }
        };
        loadSubjTask.setOnSucceeded(ev -> {
            List<SubjectInfo> subjects = loadSubjTask.getValue();
            for (SubjectInfo si : subjects)
                subjectBox.getItems().add(si.id + " - " + si.name + " (" + si.code + ")");
            if (!subjects.isEmpty()) subjectBox.setValue(subjectBox.getItems().get(0));
        });
        new Thread(loadSubjTask).start();

        // Score edit handler
        cScore.setOnEditCommit(e -> {
            MarkRow row = e.getRowValue();
            Double v = e.getNewValue();
            if (v != null && Double.isFinite(v) && v >= 0 && v <= selectedOutOf) {
                row.score = v;
                row.dirty = true;
                String result = analysisService.determineGradeAndPoints(v, selectedSubjectId, selectedExamId);
                String[] parts = result.split("\\|");
                row.grade = parts[0];
                row.points = Integer.parseInt(parts[1]);
                table.refresh();
            } else if (v != null && !Double.isFinite(v)) {
                UIUtils.showError("Invalid score value.");
            } else if (v != null) {
                UIUtils.showError("Score must be between 0 and " + selectedOutOf + ".");
            }
        });

        loadBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
            if (subjectBox.getValue() == null) { UIUtils.showError("Select a subject."); return; }
            selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            String subjVal = subjectBox.getValue();
            selectedSubjectId = Long.parseLong(subjVal.split(" - ")[0]);

            // Get out_of
            Task<Integer> outOfTask = new Task<>() {
                @Override protected Integer call() {
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "SELECT COALESCE(out_of, 100) FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
                        ps.setLong(1, selectedExamId);
                        ps.setLong(2, selectedSubjectId);
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt(1) : 100;
                    } catch (SQLException ex) { throw new RuntimeException(ex); }
                }
            };
            outOfTask.setOnSucceeded(ev2 -> {
                selectedOutOf = outOfTask.getValue();
                cScore.setText("Score / " + selectedOutOf);
                loadStudentMarks(data, table, statusLabel, spinner, form, stream);
            });
            outOfTask.setOnFailed(ev2 -> UIUtils.showError("Error: " + outOfTask.getException().getMessage()));
            new Thread(outOfTask).start();
        });

        saveBtn.setOnAction(e -> {
            List<MarkRow> dirty = data.stream().filter(r -> r.dirty).collect(Collectors.toList());
            if (dirty.isEmpty()) { UIUtils.showError("No changes to save."); return; }
            spinner.setVisible(true);
            Task<Integer> saveTask = new Task<>() {
                @Override protected Integer call() {
                    int saved = 0;
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) "
                             + "VALUES (?,?,?,?,?,?)")) {
                        for (MarkRow r : dirty) {
                            ps.setLong(1, selectedExamId);
                            ps.setLong(2, r.studentId);
                            ps.setLong(3, selectedSubjectId);
                            ps.setDouble(4, r.score);
                            ps.setString(5, r.grade);
                            ps.setInt(6, r.points);
                            ps.addBatch();
                            saved++;
                        }
                        ps.executeBatch();
                    } catch (SQLException ex) { throw new RuntimeException(ex); }
                    return saved;
                }
            };
            saveTask.setOnSucceeded(ev2 -> {
                spinner.setVisible(false);
                for (MarkRow r : dirty) r.dirty = false;
                statusLabel.setText("Saved " + saveTask.getValue() + " mark(s).");
                table.refresh();
            });
            saveTask.setOnFailed(ev2 -> {
                spinner.setVisible(false);
                UIUtils.showError("Save failed: " + saveTask.getException().getMessage());
            });
            new Thread(saveTask).start();
        });

        VBox content = new VBox(10);
        content.getChildren().addAll(header, examRow, subjectRow, loadBtn, actions, table);
        view.getChildren().addAll(backBtn, content);
        setView(view);
    }

    private void loadStudentMarks(ObservableList<MarkRow> data, TableView<MarkRow> table,
                                   Label status, ProgressIndicator spinner, int form, String stream) {
        spinner.setVisible(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                ObservableList<MarkRow> rows = FXCollections.observableArrayList();
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT s.id, s.admission_number, s.full_name, "
                         + "m.score, m.grade_achieved, m.points_achieved "
                         + "FROM students s "
                         + "LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? AND m.subject_id = ? "
                         + "WHERE s.form = ? AND s.stream = ? AND s.deallocated = 0 "
                         + "ORDER BY s.admission_number")) {
                    ps.setLong(1, selectedExamId);
                    ps.setLong(2, selectedSubjectId);
                    ps.setInt(3, form);
                    ps.setString(4, stream);
                    try (ResultSet rs = ps.executeQuery()) {
                        int pos = 0;
                        while (rs.next()) {
                            pos++;
                            rows.add(new MarkRow(pos, rs.getLong("id"),
                                rs.getString("admission_number"),
                                rs.getString("full_name"),
                                rs.getObject("score") != null ? rs.getDouble("score") : null,
                                rs.getString("grade_achieved"),
                                rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null,
                                false));
                        }
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }
                Platform.runLater(() -> {
                    data.setAll(rows);
                    table.setItems(data);
                    spinner.setVisible(false);
                    status.setText(rows.size() + " students loaded");
                });
                return null;
            }
        };
        task.setOnFailed(ev -> {
            spinner.setVisible(false);
            UIUtils.showError("Error: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    private void loadExams(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
    }

    // ───── Row / Data Classes ─────

    private record ClassInfo(int form, String stream, List<String> subjects, List<Long> subjectIds) {}
    private record SubjectInfo(long id, String name, String code) {}

    public static class MarkRow {
        private final int pos;
        private final long studentId;
        private final String admission, name;
        private Double score;
        private String grade;
        private Integer points;
        private boolean dirty;

        public MarkRow(int pos, long studentId, String admission, String name,
                       Double score, String grade, Integer points, boolean dirty) {
            this.pos = pos; this.studentId = studentId;
            this.admission = admission; this.name = name;
            this.score = score; this.grade = grade; this.points = points; this.dirty = dirty;
        }
        public int getPos() { return pos; }
        public long getStudentId() { return studentId; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Double getScore() { return score; }
        public void setScore(Double v) { this.score = v; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
        public boolean isDirty() { return dirty; }
    }

}
