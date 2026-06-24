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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import com.zaraki.exams.SeedData;
import com.zaraki.exams.auth.UserManagementForm;
import com.zaraki.exams.forms.PublishForm;
import com.zaraki.exams.forms.SchoolSettingsForm;
import com.zaraki.exams.forms.TeacherAssignmentForm;
import java.sql.*;
import java.util.*;

public class DashboardForm {

    private static final String PRIMARY = AppTheme.PRIMARY;

    private final DatabaseEngine db;
    private final SettingsManager settings;
    private final StackPane contentArea;
    private final Stage stage;
    private final ComboBox<String> curriculumSwitcher;
    private final String loggedInUser;
    private final String loggedInUsername;
    private final String loggedInRole;
    private final long loggedInUserId;
    private final Runnable onLogout;

    private StudentForm studentForm;
    private SubjectForm subjectForm;
    private ExamForm examForm;
    private GradingScaleForm gradingScaleForm;
    private UserManagementForm userManagementForm;
    private SchoolSettingsForm schoolSettingsForm;
    private TeacherAssignmentForm teacherAssignmentForm;
    private PublishForm publishForm;
    private MarksEntryForm marksEntryForm;
    private BulkMarksForm bulkMarksForm;
    private AnalysisForm analysisForm;
    private ReportForm reportForm;
    private StudentBrowserForm studentBrowserForm;
    private RecycleBinForm recycleBinForm;
    private SubjectAssignmentForm subjectAssignmentForm;

    public DashboardForm(Stage stage, String loggedInUser, String loggedInUsername, String loggedInRole, long loggedInUserId, Runnable onLogout) {
        this.stage = stage;
        this.loggedInUser = loggedInUser;
        this.loggedInUsername = loggedInUsername;
        this.loggedInRole = loggedInRole;
        this.loggedInUserId = loggedInUserId;
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
        List<String> navItems;
        if ("teacher".equals(loggedInRole)) {
            navItems = List.of("Dashboard", "Marks Entry", "Bulk Marks");
        } else {
            navItems = List.of("Dashboard", "Students", "Subjects", "Exams",
                "Grading Scales", "Users", "Teacher Subjects", "Streams",
                "Stream Subjects", "Settings", "Publish", "Marks Entry",
                "Bulk Marks", "Analysis", "Reports", "Browse Students", "Recycle Bin");
        }

        for (String itemName : navItems) {
            Label lbl = new Label("  " + itemName);
            lbl.setFont(Font.font("System", 14));
            lbl.setTextFill(Color.rgb(255, 255, 255, 0.75));
            lbl.setPadding(new Insets(10, 15, 10, 15));
            lbl.setPrefWidth(210);
            lbl.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");
            String page = itemName.toLowerCase().replace(" ", "_");
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

    private final ExamAnalysisService analysisService = new ExamAnalysisService();

    private void showDashboard() {
        VBox view = new VBox(20);

        Label header = new Label("Dashboard");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label welcome = new Label("Welcome to Thorium Exam Analysis System v2.\n"
            + "Active curriculum: " + settings.getCurriculum().getDisplayName()
            + "\nUse the sidebar to navigate.");
        welcome.setFont(Font.font("System", 14));
        welcome.setTextFill(Color.gray(0.4));
        welcome.setWrapText(true);

        HBox cards = new HBox(20);
        VBox[] cardBoxes = {
            statCard("Students", "…"),
            statCard("Subjects", "…"),
            statCard("Exams", "…"),
            statCard("Marks Entered", "…")
        };
        cards.getChildren().addAll(cardBoxes);
        cardBoxes[0].setOnMouseClicked(e -> showStudentBrowser());
        cardBoxes[0].setCursor(javafx.scene.Cursor.HAND);
        cardBoxes[0].setOnMouseEntered(e -> cardBoxes[0].setStyle(
            "-fx-background-color: #e8eaf6; -fx-background-radius: 10; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"));
        cardBoxes[0].setOnMouseExited(e -> cardBoxes[0].setStyle(
            "-fx-background-color: white; -fx-background-radius: 10; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);"));
        // Load counts in background
        Task<int[]> countTask = new Task<>() {
            @Override protected int[] call() {
                return new int[]{ count("students"), count("subjects"), count("exams"), count("marks") };
            }
        };
        countTask.setOnSucceeded(ev -> {
            int[] counts = countTask.getValue();
            for (int i = 0; i < 4; i++) {
                VBox card = (VBox)cards.getChildren().get(i);
                Label valLabel = new Label(String.valueOf(counts[i]));
                valLabel.setFont(Font.font("System", FontWeight.BOLD, 30));
                String[] titles = {"Students", "Subjects", "Exams", "Marks Entered"};
                Label titleLabel = new Label(titles[i]);
                titleLabel.setFont(Font.font("System", 13));
                card.getChildren().setAll(valLabel, titleLabel);
            }
        });
        new Thread(countTask).start();

        VBox trendSection = buildExamAnalytics();

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
            Task<Integer> task = new Task<>() {
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
            Task<String> task = new Task<>() {
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

    private VBox buildExamAnalytics() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(20, 0, 0, 0));

        Label title = new Label("Exam Analytics");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        HBox controls = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        Label maxMarksLabel = new Label();
        maxMarksLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        maxMarksLabel.setTextFill(Color.web(PRIMARY));
        controls.getChildren().addAll(new Label("Exam:"), examBox, maxMarksLabel);
        loadExamList(examBox);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        // Top Subjects by Average
        Label topLabel = new Label("Top Subjects by Average");
        topLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        TableView<Object[]> topTable = new TableView<>();
        topTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Object[], String> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue()[0])));
        rankCol.setPrefWidth(40);
        TableColumn<Object[], String> subjCol = new TableColumn<>("Subject");
        subjCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[1]));
        TableColumn<Object[], String> avgCol = new TableColumn<>("Average");
        avgCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.1f", d.getValue()[2])));
        avgCol.setPrefWidth(80);
        TableColumn<Object[], String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[3]));
        gradeCol.setPrefWidth(60);
        topTable.getColumns().addAll(rankCol, subjCol, avgCol, gradeCol);
        topTable.setPrefHeight(150);

        // Most Improved Stream
        Label improvedLabel = new Label("Most Improved Stream");
        improvedLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        Label improvedValue = new Label();

        // Per-Stream Subject Summary
        Label summaryLabel = new Label("Per-Stream Subject Summary");
        summaryLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        TableView<Object[]> summaryTable = new TableView<>();
        summaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Object[], String> fsCol = new TableColumn<>("Form/Stream");
        fsCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[0]));
        fsCol.setPrefWidth(100);
        TableColumn<Object[], String> ssCol = new TableColumn<>("Subject");
        ssCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[1]));
        TableColumn<Object[], String> saCol = new TableColumn<>("Average");
        saCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.1f", d.getValue()[2])));
        saCol.setPrefWidth(80);
        TableColumn<Object[], String> sgCol = new TableColumn<>("Grade");
        sgCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[3]));
        sgCol.setPrefWidth(60);
        summaryTable.getColumns().addAll(fsCol, ssCol, saCol, sgCol);
        summaryTable.setPrefHeight(200);

        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            maxMarksLabel.setText("Max Marks: " + getExamMaxMarks(selectedExamId));
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    ObservableList<Object[]> topData = FXCollections.observableArrayList();
                    ObservableList<Object[]> summaryData = FXCollections.observableArrayList();
                    String[] improvement = {""};
                    try (Connection conn = db.getConnection()) {
                        // Top subjects
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT su.subject_name, AVG(m.score) as avg_score, su.id as subject_id "
                            + "FROM marks m JOIN subjects su ON su.id = m.subject_id "
                            + "WHERE m.exam_id = ? GROUP BY m.subject_id ORDER BY avg_score DESC")) {
                            ps.setLong(1, selectedExamId);
                            try (ResultSet rs = ps.executeQuery()) {
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
                        }

                        // Most improved stream
                        try (Statement st = conn.createStatement();
                             ResultSet rs = st.executeQuery("SELECT id FROM exams ORDER BY id DESC LIMIT 2")) {
                            long[] eids = new long[2];
                            int idx = 0;
                            while (rs.next() && idx < 2) eids[idx++] = rs.getLong("id");
                            if (idx == 2) {
                                Map<String, Double> avg1 = getStreamAverages(conn, eids[0]);
                                Map<String, Double> avg2 = getStreamAverages(conn, eids[1]);
                                String bestStream = "";
                                double bestImprovement = -Double.MAX_VALUE;
                                for (var entry : avg1.entrySet()) {
                                    double prev = avg2.getOrDefault(entry.getKey(), 0.0);
                                    double diff = entry.getValue() - prev;
                                    if (diff > bestImprovement) {
                                        bestImprovement = diff;
                                        bestStream = entry.getKey();
                                    }
                                }
                                if (!bestStream.isEmpty()) {
                                    improvement[0] = bestStream + " improved by " + String.format("%.1f", bestImprovement) + " points";
                                }
                            }
                        }

                        // Per-stream subject summary
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT s.form, s.stream, su.subject_name, AVG(m.score) as avg_score, su.id as subject_id "
                            + "FROM marks m JOIN students s ON s.id = m.student_id "
                            + "JOIN subjects su ON su.id = m.subject_id "
                            + "WHERE m.exam_id = ? "
                            + "GROUP BY s.form, s.stream, m.subject_id "
                            + "ORDER BY s.form, s.stream, su.subject_name")) {
                            ps.setLong(1, selectedExamId);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    String fs = "F" + rs.getInt("form") + " " + rs.getString("stream");
                                    String subj = rs.getString("subject_name");
                                    double avg = rs.getDouble("avg_score");
                                    long sid = rs.getLong("subject_id");
                                    String grade = analysisService.determineGradeAndPoints(avg, sid, selectedExamId).split("\\|")[0];
                                    summaryData.add(new Object[]{fs, subj, avg, grade});
                                }
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
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                showAlert("Analytics error: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        section.getChildren().addAll(title, controls, spinner, topLabel, topTable,
            improvedLabel, improvedValue, summaryLabel, summaryTable);
        return section;
    }

    private void loadExamList(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private Map<String, Double> getStreamAverages(Connection conn, long examId) throws SQLException {
        Map<String, Double> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT s.form, s.stream, AVG(m.score) as avg_score "
            + "FROM marks m JOIN students s ON s.id = m.student_id "
            + "WHERE m.exam_id = ? GROUP BY s.form, s.stream")) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    map.put("F" + rs.getInt("form") + " " + rs.getString("stream"), rs.getDouble("avg_score"));
            }
        }
        return map;
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

    private void showTeacherSubjects() {
        teacherAssignmentForm = new TeacherAssignmentForm(db);
        setContent(teacherAssignmentForm.getView());
    }

    private void showStreams() {
        setContent(new StreamManagementForm(db).getView());
    }

    private void showStreamSubjects() {
        subjectAssignmentForm = new SubjectAssignmentForm(db);
        setContent(subjectAssignmentForm.getView());
    }

    private void showSettings() {
        schoolSettingsForm = new SchoolSettingsForm();
        setContent(schoolSettingsForm.getView());
    }

    private void showPublish() {
        publishForm = new PublishForm(db, loggedInUser, loggedInUsername, loggedInRole);
        setContent(publishForm.getView());
    }

    private void showMarksEntry() {
        marksEntryForm = new MarksEntryForm(db, loggedInUserId, loggedInRole);
        setContent(marksEntryForm.getView());
    }

    private void showBulkMarks() {
        bulkMarksForm = new BulkMarksForm(db, stage, loggedInUserId, loggedInRole);
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

    private void showStudentBrowser() {
        studentBrowserForm = new StudentBrowserForm(db, this::showDashboard);
        setContent(studentBrowserForm.getView());
    }

    private void showRecycleBin() {
        recycleBinForm = new RecycleBinForm(db, this::showDashboard);
        setContent(recycleBinForm.getView());
    }

    private void showTeacherDashboard() {
        TeacherDashboardForm form = new TeacherDashboardForm(db, loggedInUser, loggedInUsername,
            loggedInUserId, () -> { if (onLogout != null) onLogout.run(); });
        setContent(form.getView());
    }

    private void setContent(javafx.scene.Node node) {
        contentArea.getChildren().setAll(node);
    }

    private int getExamMaxMarks(long examId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(SUM(COALESCE(out_of, 100)), 0) FROM exam_subjects WHERE exam_id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private int count(String table) {
        Map<String, String> allowed = Map.of(
            "students", "students",
            "subjects", "subjects",
            "exams", "exams",
            "marks", "marks"
        );
        String actualTable = allowed.get(table);
        if (actualTable == null) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + actualTable);
             ResultSet rs = ps.executeQuery()) {
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
