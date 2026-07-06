package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.IExamRepository;
import com.zaraki.exams.repository.ExamRepositoryImpl;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.service.ExamAnalysisServiceImpl;
import com.zaraki.exams.auth.UserManagementForm;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;

public class DashboardForm {

    private final Stage stage;
    private final String loggedInUser, loggedInUsername, loggedInRole;
    private final long loggedInUserId;
    private final Runnable onLogout;
    private final DatabaseEngine db;
    private final SettingsManager settings;
    private final IExamRepository examRepo;
    private final StackPane contentArea;
    private final IExamAnalysisService analysisService = new ExamAnalysisServiceImpl();

    private DashboardTopBar topBar;
    private DashboardNavBar navBar;
    private DashboardStatCards statCards;
    private DashboardDemoDataPanel demoPanel;
    private VBox root;
    private boolean darkMode;

    // Lazy forms
    private StudentForm studentForm;
    private SubjectForm subjectForm;
    private ExamForm examForm;
    private GradingScaleForm gradingScaleForm;
    private UserManagementForm userManagementForm;
    private com.zaraki.exams.forms.TeacherAssignmentForm teacherAssignmentForm;
    private com.zaraki.exams.forms.SchoolSettingsForm schoolSettingsForm;
    private PublishForm publishForm;
    private MarksEntryForm marksEntryForm;
    private BulkMarksForm bulkMarksForm;
    private AnalysisForm analysisForm;
    private ReportForm reportForm;
    private StudentBrowserForm studentBrowserForm;
    private RecycleBinForm recycleBinForm;
    private com.zaraki.exams.forms.SubjectAssignmentForm subjectAssignmentForm;
    private com.zaraki.exams.forms.TeacherDashboardForm teacherDashboardForm;

    public DashboardForm(Stage stage, String loggedInUser, String loggedInUsername,
                         String loggedInRole, long loggedInUserId, Runnable onLogout) {
        this.stage = stage;
        this.loggedInUser = loggedInUser;
        this.loggedInUsername = loggedInUsername;
        this.loggedInRole = loggedInRole;
        this.loggedInUserId = loggedInUserId;
        this.onLogout = onLogout;
        this.db = DatabaseEngine.getInstance();
        this.settings = new SettingsManager();
        this.examRepo = new ExamRepositoryImpl();
        this.contentArea = new StackPane();
        this.contentArea.getStyleClass().add("content-area");
        this.darkMode = "true".equals(settings.getSetting("dark_mode", "false"));
    }

    public VBox getView() {
        root = new VBox();
        root.getStyleClass().add("root");
        topBar = new DashboardTopBar(loggedInUser, darkMode, onLogout, this::toggleDarkMode);
        navBar = new DashboardNavBar(loggedInRole, this::navigate);
        statCards = new DashboardStatCards(db);
        demoPanel = new DashboardDemoDataPanel();
        demoPanel.setOnDataChanged(this::showDashboard);

        Node content = createContent();
        VBox.setVgrow(content, Priority.ALWAYS);
        root.getChildren().addAll(topBar.getView(), navBar.getView(), content);

        if (darkMode) {
            Platform.runLater(() -> { root.getStyleClass().add("dark-mode"); applyDarkModeToScene(true); });
        }
        return root;
    }

    private void toggleDarkMode() {
        darkMode = !darkMode;
        settings.setSetting("dark_mode", String.valueOf(darkMode));
        root.getStyleClass().remove("dark-mode");
        if (darkMode) root.getStyleClass().add("dark-mode");
        topBar.setDarkModeText(darkMode);
        applyDarkModeToScene(darkMode);
    }

    private void applyDarkModeToScene(boolean on) {
        Scene scene = root.getScene();
        if (scene == null) return;
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
    }

    private ScrollPane createContent() {
        contentArea.setPadding(new Insets(30, 40, 30, 40));
        showDashboard();
        ScrollPane sp = new ScrollPane(contentArea);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent; -fx-border: none;");
        return sp;
    }

    private void navigate(String page) {
        if ("teacher".equals(loggedInRole) && "dashboard".equals(page)) {
            showTeacherDashboard();
            return;
        }
        switch (page) {
            case "dashboard" -> showDashboard();
            case "students" -> showStudents();
            case "subjects" -> showSubjects();
            case "exams" -> showExams();
            case "grading_scales" -> showGradingScales();
            case "users" -> showUsers();
            case "teacher_subjects" -> showTeacherSubjects();
            case "streams" -> showStreams();
            case "stream_subjects" -> showStreamSubjects();
            case "settings" -> showSettings();
            case "publish" -> showPublish();
            case "marks_entry" -> showMarksEntry();
            case "bulk_marks" -> showBulkMarks();
            case "analysis" -> showAnalysis();
            case "reports" -> showReports();
            case "browse_students" -> showStudentBrowser();
            case "recycle_bin" -> showRecycleBin();
        }
    }

    // ─── Dashboard ─────────────────────────────────────────────

    private void showDashboard() {
        VBox view = new VBox(20);
        Label header = new Label("\uD83C\uDFE0  Dashboard");
        header.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.getStyleClass().add("page-header");

        Label welcome = new Label("Welcome to Thorium Exam Analysis System v2.\n"
            + "Active curriculum: " + settings.getCurriculum().getDisplayName());
        welcome.setFont(Font.font("System", 14));
        welcome.setTextFill(Color.gray(0.4));
        welcome.setWrapText(true);

        statCards.setOnStudentsClick(() -> navigate("students"));
        statCards.setOnSubjectsClick(() -> navigate("subjects"));
        statCards.setOnExamsClick(() -> navigate("exams"));
        statCards.setOnMarksClick(() -> navigate("marks_entry"));
        statCards.load();

        VBox trendSection = buildExamAnalytics();
        view.getChildren().addAll(header, welcome, statCards.getView(), trendSection, demoPanel.getView());
        contentArea.getChildren().setAll(view);
    }

    private VBox buildExamAnalytics() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(20, 0, 0, 0));

        Label title = new Label("\uD83D\uDCCA  Exam Analytics");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        HBox controls = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        Label maxMarksLabel = new Label();
        maxMarksLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        maxMarksLabel.setTextFill(Color.web(AppTheme.PRIMARY));
        controls.getChildren().addAll(new Label("Exam:"), examBox, maxMarksLabel);
        loadExamList(examBox);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        Label topLabel = new Label("\uD83C\uDFC6  Top Subjects by Average");
        topLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        TableView<Object[]> topTable = new TableView<>();
        topTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        topTable.getColumns().addAll(
            col("#", 40, 0), col("Subject", 0, 1), col("Average", 80, 2), col("Grade", 60, 3));
        topTable.setPrefHeight(150);

        Label improvedLabel = new Label("\uD83D\uDCC8  Most Improved Stream");
        improvedLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        Label improvedValue = new Label();

        Label summaryLabel = new Label("\uD83D\uDCCA  Per-Stream Subject Summary");
        summaryLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        TableView<Object[]> summaryTable = new TableView<>();
        summaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        summaryTable.getColumns().addAll(
            col("Form/Stream", 100, 0), col("Subject", 0, 1), col("Average", 80, 2), col("Grade", 60, 3));
        summaryTable.setPrefHeight(200);

        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            maxMarksLabel.setText("Max Marks: " + getExamMaxMarks(selectedExamId));
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    var topData = FXCollections.<Object[]>observableArrayList();
                    var summaryData = FXCollections.<Object[]>observableArrayList();
                    String[] improvement = {""};
                    try (Connection conn = db.getConnection()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT su.subject_name, AVG(m.score) as avg_score, su.id as subject_id "
                            + "FROM marks m JOIN subjects su ON su.id = m.subject_id "
                            + "WHERE m.exam_id = ? GROUP BY m.subject_id ORDER BY avg_score DESC")) {
                            ps.setLong(1, selectedExamId);
                            ResultSet rs = ps.executeQuery();
                            int rank = 0;
                            while (rs.next()) {
                                rank++;
                                String name = rs.getString("subject_name");
                                double avg = rs.getDouble("avg_score");
                                long subjId = rs.getLong("subject_id");
                                String grade = analysisService.determineGradeAndPoints(avg, subjId, selectedExamId).split("\\|")[0];
                                topData.add(new Object[]{rank, name, avg, grade});
                            }
                        }
                        try (Statement st = conn.createStatement();
                             ResultSet rs = st.executeQuery("SELECT id FROM exams ORDER BY id DESC LIMIT 2")) {
                            long[] eids = new long[2];
                            int idx = 0;
                            while (rs.next() && idx < 2) eids[idx++] = rs.getLong("id");
                            if (idx == 2) {
                                Map<String, Double> avg1 = getStreamAverages(conn, eids[0]);
                                Map<String, Double> avg2 = getStreamAverages(conn, eids[1]);
                                String best = "";
                                double bestImp = -Double.MAX_VALUE;
                                for (var entry : avg1.entrySet()) {
                                    double diff = entry.getValue() - avg2.getOrDefault(entry.getKey(), 0.0);
                                    if (diff > bestImp) { bestImp = diff; best = entry.getKey(); }
                                }
                                if (!best.isEmpty()) improvement[0] = best + " improved by " + String.format("%.1f", bestImp) + " points";
                            }
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT s.form, s.stream, su.subject_name, AVG(m.score) as avg_score, su.id as subject_id "
                            + "FROM marks m JOIN students s ON s.id = m.student_id "
                            + "JOIN subjects su ON su.id = m.subject_id "
                            + "WHERE m.exam_id = ? GROUP BY s.form, s.stream, m.subject_id ORDER BY s.form, s.stream, su.subject_name")) {
                            ps.setLong(1, selectedExamId);
                            ResultSet rs = ps.executeQuery();
                            while (rs.next()) {
                                String fs = "Form " + rs.getInt("form") + " " + rs.getString("stream");
                                String subj = rs.getString("subject_name");
                                double avg = rs.getDouble("avg_score");
                                long sid = rs.getLong("subject_id");
                                String grade = analysisService.determineGradeAndPoints(avg, sid, selectedExamId).split("\\|")[0];
                                summaryData.add(new Object[]{fs, subj, avg, grade});
                            }
                        }
                    } catch (SQLException ex) { throw new RuntimeException(ex); }
                    final String imp = improvement[0];
                    Platform.runLater(() -> {
                        topTable.setItems(topData);
                        summaryTable.setItems(summaryData);
                        improvedValue.setText(imp.isEmpty() ? "Need at least 2 exams with marks" : imp);
                        spinner.setVisible(false);
                    });
                    return null;
                }
            };
            task.setOnFailed(ev -> { spinner.setVisible(false); showAlert("Analytics error: " + task.getException().getMessage()); });
            new Thread(task).start();
        });

        section.getChildren().addAll(title, controls, spinner, topLabel, topTable, improvedLabel, improvedValue, summaryLabel, summaryTable);
        return section;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Object[], T> col(String name, int width, int idx) {
        TableColumn<Object[], T> c = new TableColumn<>(name);
        int i = idx;
        c.setCellValueFactory(d -> {
            Object v = d.getValue()[i];
            return v instanceof Number n ? (javafx.beans.property.SimpleObjectProperty<T>) new javafx.beans.property.SimpleObjectProperty<>((T) n)
                : new javafx.beans.property.SimpleObjectProperty<>((T) v);
        });
        if (width > 0) c.setPrefWidth(width);
        return c;
    }

    private void loadExamList(ComboBox<String> box) {
        try {
            var exams = examRepo.findAllDesc();
            for (var e : exams)
                box.getItems().add(e.get("id") + " - " + e.get("academic_year") + " " + e.get("term") + " " + e.get("exam_series"));
        } catch (Exception ex) { showAlert(ex.getMessage()); }
    }

    private Map<String, Double> getStreamAverages(Connection conn, long examId) throws SQLException {
        Map<String, Double> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT s.form, s.stream, AVG(m.score) as avg_score FROM marks m JOIN students s ON s.id = m.student_id WHERE m.exam_id = ? GROUP BY s.form, s.stream")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put("Form " + rs.getInt("form") + " " + rs.getString("stream"), rs.getDouble("avg_score"));
        }
        return map;
    }

    private int getExamMaxMarks(long examId) { return examRepo.getMaxMarks(examId); }

    // ─── Navigation helpers ────────────────────────────────────

    private void setContent(Node node) { contentArea.getChildren().setAll(node); }

    private void showStudents() { studentForm = new StudentForm(db, stage); setContent(studentForm.getView()); }
    private void showSubjects() { subjectForm = new SubjectForm(db); setContent(subjectForm.getView()); }
    private void showExams() { examForm = new ExamForm(db); setContent(examForm.getView()); }
    private void showGradingScales() { gradingScaleForm = new GradingScaleForm(settings); setContent(gradingScaleForm.getView()); }
    private void showUsers() { userManagementForm = new UserManagementForm(); setContent(userManagementForm.getView()); }
    private void showTeacherSubjects() { teacherAssignmentForm = new TeacherAssignmentForm(); setContent(teacherAssignmentForm.getView()); }
    private void showStreams() { setContent(new com.zaraki.exams.forms.StreamManagementForm(db).getView()); }
    private void showStreamSubjects() { subjectAssignmentForm = new com.zaraki.exams.forms.SubjectAssignmentForm(db); setContent(subjectAssignmentForm.getView()); }
    private void showSettings() { schoolSettingsForm = new com.zaraki.exams.forms.SchoolSettingsForm(); setContent(schoolSettingsForm.getView()); }
    private void showPublish() { publishForm = new PublishForm(db, loggedInUser, loggedInUsername, loggedInRole); setContent(publishForm.getView()); }
    private void showMarksEntry() { marksEntryForm = new MarksEntryForm(db, loggedInUserId, loggedInRole); setContent(marksEntryForm.getView()); }
    private void showBulkMarks() { bulkMarksForm = new BulkMarksForm(db, stage, loggedInUserId, loggedInRole); setContent(bulkMarksForm.getView()); }
    private void showAnalysis() { analysisForm = new AnalysisForm(db, stage); setContent(analysisForm.getView()); }
    private void showReports() { reportForm = new ReportForm(db, stage); setContent(reportForm.getView()); }
    private void showStudentBrowser() { studentBrowserForm = new StudentBrowserForm(db, this::showDashboard); setContent(studentBrowserForm.getView()); }
    private void showRecycleBin() { recycleBinForm = new RecycleBinForm(this::showDashboard); setContent(recycleBinForm.getView()); }
    private void showTeacherDashboard() {
        teacherDashboardForm = new com.zaraki.exams.forms.TeacherDashboardForm(db, loggedInUser, loggedInUsername,
            loggedInUserId, () -> { if (onLogout != null) onLogout.run(); });
        setContent(teacherDashboardForm.getView());
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION, msg); a.showAndWait(); });
    }
}
