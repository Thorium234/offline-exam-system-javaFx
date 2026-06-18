package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import com.zaraki.exams.SeedData;
import com.zaraki.exams.auth.UserManagementForm;
import java.sql.*;
import java.util.List;

public class DashboardForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final SettingsManager settings;
    private final StackPane contentArea;
    private final Stage stage;
    private final ComboBox<String> curriculumSwitcher;
    private final String loggedInUser;
    private final Runnable onLogout;

    private StudentForm studentForm;
    private SubjectForm subjectForm;
    private ExamForm examForm;
    private GradingScaleForm gradingScaleForm;
    private UserManagementForm userManagementForm;
    private MarksEntryForm marksEntryForm;
    private BulkMarksForm bulkMarksForm;
    private AnalysisForm analysisForm;
    private ReportForm reportForm;

    public DashboardForm(Stage stage, String loggedInUser, Runnable onLogout) {
        this.stage = stage;
        this.loggedInUser = loggedInUser;
        this.onLogout = onLogout;
        this.db = DatabaseEngine.getInstance();
        this.settings = new SettingsManager();
        this.contentArea = new StackPane();
        this.contentArea.getStyleClass().add("content-area");
        this.curriculumSwitcher = new ComboBox<>();
    }

    public ScrollPane getView() {
        HBox root = new HBox();
        root.getChildren().addAll(createSidebar(), createContent());
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent; -fx-border: none;");
        return sp;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.setPrefWidth(230);
        sidebar.setPadding(new Insets(20, 12, 20, 12));
        sidebar.setStyle("-fx-background-color: " + PRIMARY + ";");

        Label title = new Label("THORIUM");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("Exam Analysis");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.rgb(255, 255, 255, 0.6));
        sub.setPadding(new Insets(0, 0, 10, 0));

        Label userBadge = new Label("Logged in as " + loggedInUser);
        userBadge.setFont(Font.font("System", 11));
        userBadge.setTextFill(Color.rgb(255, 255, 255, 0.5));
        userBadge.setPadding(new Insets(0, 0, 5, 0));

        curriculumSwitcher.setItems(FXCollections.observableArrayList(
            CurriculumSystem.SYSTEM_844.getDisplayName(),
            CurriculumSystem.CBC.getDisplayName()
        ));
        CurriculumSystem current = settings.getCurriculum();
        curriculumSwitcher.setValue(current.getDisplayName());
        curriculumSwitcher.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;");
        curriculumSwitcher.setOnAction(e -> onCurriculumChanged());

        VBox nav = new VBox(2);
        nav.setPadding(new Insets(10, 0, 0, 0));
        String[][] items = {
            {"Dashboard", ""},
            {"Students", ""},
            {"Subjects", ""},
            {"Exams", ""},
            {"Grading Scales", ""},
            {"Users", ""},
            {"Marks Entry", ""},
            {"Bulk Marks", ""},
            {"Analysis", ""},
            {"Reports", ""}
        };

        for (String[] item : items) {
            Label lbl = new Label("  " + item[0]);
            lbl.setFont(Font.font("System", 14));
            lbl.setTextFill(Color.rgb(255, 255, 255, 0.75));
            lbl.setPadding(new Insets(10, 15, 10, 15));
            lbl.setPrefWidth(210);
            lbl.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");
            String page = item[0].toLowerCase().replace(" ", "_");
            lbl.setOnMouseEntered(e ->
                lbl.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 6;"));
            lbl.setOnMouseExited(e ->
                lbl.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;"));
            lbl.setOnMouseClicked(e -> navigate(page));
            nav.getChildren().add(lbl);
        }

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("Logout");
        logoutBtn.setPrefWidth(210);
        logoutBtn.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-font-size: 13; -fx-background-radius: 6;");
        logoutBtn.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });

        sidebar.getChildren().addAll(title, sub, userBadge, curriculumSwitcher, nav, spacer, logoutBtn);
        return sidebar;
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

    private void onCurriculumChanged() {
        String val = curriculumSwitcher.getValue();
        CurriculumSystem cs = val.equals(CurriculumSystem.CBC.getDisplayName())
            ? CurriculumSystem.CBC : CurriculumSystem.SYSTEM_844;
        settings.setCurriculum(cs);
        if (gradingScaleForm != null) gradingScaleForm.refresh();
        showAlert("Curriculum changed to " + cs.getDisplayName());
    }

    private void navigate(String page) {
        switch (page) {
            case "dashboard" -> showDashboard();
            case "students" -> showStudents();
            case "subjects" -> showSubjects();
            case "exams" -> showExams();
            case "grading_scales" -> showGradingScales();
            case "users" -> showUsers();
            case "marks_entry" -> showMarksEntry();
            case "bulk_marks" -> showBulkMarks();
            case "analysis" -> showAnalysis();
            case "reports" -> showReports();
        }
    }

    private final ExamAnalysisService analysisService = new ExamAnalysisService();

    private void showDashboard() {
        VBox view = new VBox(20);

        Label header = new Label("Dashboard");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        int studentCount = count("students");
        int subjectCount = count("subjects");
        int examCount = count("exams");
        int markCount = count("marks");

        HBox cards = new HBox(20);
        cards.getChildren().addAll(
            statCard("Students", String.valueOf(studentCount)),
            statCard("Subjects", String.valueOf(subjectCount)),
            statCard("Exams", String.valueOf(examCount)),
            statCard("Marks Entered", String.valueOf(markCount))
        );

        VBox trendSection = buildTrendSection();

        Label welcome = new Label("Welcome to Thorium Exam Analysis System v2.\n"
            + "Active curriculum: " + settings.getCurriculum().getDisplayName()
            + "\nUse the sidebar to navigate.");
        welcome.setFont(Font.font("System", 14));
        welcome.setTextFill(Color.gray(0.4));
        welcome.setWrapText(true);

        // Demo data section
        VBox demoBox = new VBox(8);
        demoBox.setPadding(new Insets(15));
        demoBox.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 8; -fx-border-color: #ffcc80; -fx-border-radius: 8;");
        Label demoLabel = new Label("Demo Data Tools");
        demoLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        demoLabel.setTextFill(Color.web("#e65100"));

        HBox demoBtns = new HBox(10);
        Button seedStudentsBtn = new Button("Generate Students (20/stream)");
        Button seedMarksBtn = new Button("Generate Marks for Exam 1");
        ProgressIndicator demoSpinner = new ProgressIndicator();
        demoSpinner.setVisible(false);
        demoSpinner.setPrefSize(20, 20);
        Label demoStatus = new Label();
        demoStatus.setFont(Font.font("System", 12));
        demoStatus.setTextFill(Color.gray(0.5));
        demoBtns.getChildren().addAll(seedStudentsBtn, seedMarksBtn, demoSpinner);

        seedStudentsBtn.setOnAction(e -> {
            demoSpinner.setVisible(true);
            demoStatus.setText("Generating students...");
            javafx.concurrent.Task<Integer> task = new javafx.concurrent.Task<>() {
                @Override protected Integer call() throws Exception {
                    return new SeedData().seedAll();
                }
            };
            task.setOnSucceeded(ev -> {
                demoSpinner.setVisible(false);
                demoStatus.setText("Generated " + task.getValue() + " students across 4 forms.");
                showDashboard();
            });
            task.setOnFailed(ev -> { demoSpinner.setVisible(false); demoStatus.setText("Error: " + task.getException().getMessage()); });
            new Thread(task).start();
        });

        seedMarksBtn.setOnAction(e -> {
            demoSpinner.setVisible(true);
            demoStatus.setText("Generating marks...");
            javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
                @Override protected String call() throws Exception {
                    SeedData sd = new SeedData();
                    long eid = sd.getFirstExamId();
                    if (eid < 0) return "No exams found. Create an exam first.";
                    int count = sd.seedMarks(eid);
                    return "Generated " + count + " marks for exam " + eid + ".";
                }
            };
            task.setOnSucceeded(ev -> {
                demoSpinner.setVisible(false);
                demoStatus.setText(task.getValue());
                showDashboard();
            });
            task.setOnFailed(ev -> { demoSpinner.setVisible(false); demoStatus.setText("Error: " + task.getException().getMessage()); });
            new Thread(task).start();
        });

        demoBox.getChildren().addAll(demoLabel, demoBtns, demoStatus);
        view.getChildren().addAll(header, welcome, cards, trendSection, demoBox);
        contentArea.getChildren().setAll(view);
    }

    private VBox buildTrendSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(20, 0, 0, 0));

        Label trendLabel = new Label("Performance Trend");
        trendLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        HBox controls = new HBox(10);
        ComboBox<String> studentBox = new ComboBox<>();
        studentBox.setPromptText("Select Student");
        studentBox.setPrefWidth(300);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, admission_number, full_name FROM students ORDER BY admission_number")) {
            while (rs.next())
                studentBox.getItems().add(rs.getLong("id") + " - " + rs.getString("admission_number")
                    + " | " + rs.getString("full_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Exam #");
        xAxis.setTickUnit(1);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Total Points");
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Total Points per Exam");
        chart.setPrefHeight(300);
        chart.setAnimated(false);
        chart.setLegendVisible(false);

        controls.getChildren().addAll(new Label("Student:"), studentBox);

        studentBox.setOnAction(e -> {
            if (studentBox.getValue() == null) return;
            long studentId = Long.parseLong(studentBox.getValue().split(" - ")[0]);
            Task<List<ExamAnalysisService.StudentTrend>> task = new Task<>() {
                @Override protected List<ExamAnalysisService.StudentTrend> call() {
                    return analysisService.computeStudentTrend(studentId);
                }
            };
            task.setOnSucceeded(ev -> {
                List<ExamAnalysisService.StudentTrend> data = task.getValue();
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName("Total Points");
                for (int i = 0; i < data.size(); i++) {
                    series.getData().add(new XYChart.Data<>(i + 1, data.get(i).totalPoints()));
                }
                chart.getData().clear();
                chart.getData().add(series);

                if (!data.isEmpty()) {
                    double minY = data.stream().mapToDouble(ExamAnalysisService.StudentTrend::totalPoints).min().orElse(0);
                    double maxY = data.stream().mapToDouble(ExamAnalysisService.StudentTrend::totalPoints).max().orElse(0);
                    double pad = Math.max((maxY - minY) * 0.15, 2);
                    yAxis.setAutoRanging(false);
                    yAxis.setLowerBound(Math.max(0, minY - pad));
                    yAxis.setUpperBound(maxY + pad);
                    yAxis.setTickUnit(Math.max(1, (maxY - minY + 2 * pad) / 8));
                }
            });
            task.setOnFailed(ev -> showAlert(task.getException().getMessage()));
            new Thread(task).start();
        });

        section.getChildren().addAll(trendLabel, controls, chart);
        return section;
    }

    private VBox statCard(String title, String value) {
        VBox card = new VBox(5);
        card.setPrefSize(220, 100);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        Label val = new Label(value);
        val.setFont(Font.font("System", FontWeight.BOLD, 30));
        val.setTextFill(Color.web(PRIMARY));
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", 13));
        lbl.setTextFill(Color.gray(0.5));
        card.getChildren().addAll(val, lbl);
        return card;
    }

    private void showStudents() {
        studentForm = new StudentForm(db, stage);
        setContent(studentForm.getView());
    }

    private void showSubjects() {
        subjectForm = new SubjectForm(db);
        setContent(subjectForm.getView());
    }

    private void showExams() {
        examForm = new ExamForm(db);
        setContent(examForm.getView());
    }

    private void showGradingScales() {
        gradingScaleForm = new GradingScaleForm(db, settings);
        setContent(gradingScaleForm.getView());
    }

    private void showUsers() {
        userManagementForm = new UserManagementForm();
        setContent(userManagementForm.getView());
    }

    private void showMarksEntry() {
        marksEntryForm = new MarksEntryForm(db);
        setContent(marksEntryForm.getView());
    }

    private void showBulkMarks() {
        bulkMarksForm = new BulkMarksForm(db, stage);
        setContent(bulkMarksForm.getView());
    }

    private void showAnalysis() {
        analysisForm = new AnalysisForm(db, stage);
        setContent(analysisForm.getView());
    }

    private void showReports() {
        reportForm = new ReportForm(db, stage);
        setContent(reportForm.getView());
    }

    private void setContent(javafx.scene.Node node) {
        contentArea.getChildren().setAll(node);
    }

    private int count(String table) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        } catch (SQLException e) { return 0; }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
            a.showAndWait();
        });
    }
}
