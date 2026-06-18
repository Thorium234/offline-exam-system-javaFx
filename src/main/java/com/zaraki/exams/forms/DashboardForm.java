package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.sql.*;

public class DashboardForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final SettingsManager settings;
    private final StackPane contentArea;
    private final Stage stage;
    private final ComboBox<String> curriculumSwitcher;

    private StudentForm studentForm;
    private SubjectForm subjectForm;
    private ExamForm examForm;
    private GradingScaleForm gradingScaleForm;
    private MarksEntryForm marksEntryForm;
    private AnalysisForm analysisForm;
    private ReportForm reportForm;

    public DashboardForm(Stage stage) {
        this.stage = stage;
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
            {"Marks Entry", ""},
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

        sidebar.getChildren().addAll(title, sub, curriculumSwitcher, nav);
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
            case "marks_entry" -> showMarksEntry();
            case "analysis" -> showAnalysis();
            case "reports" -> showReports();
        }
    }

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

        Label welcome = new Label("Welcome to Thorium Exam Analysis System v2.\n"
            + "Active curriculum: " + settings.getCurriculum().getDisplayName()
            + "\nUse the sidebar to navigate.");
        welcome.setFont(Font.font("System", 14));
        welcome.setTextFill(Color.gray(0.4));
        welcome.setWrapText(true);

        view.getChildren().addAll(header, welcome, cards);
        contentArea.getChildren().setAll(view);
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
        studentForm = new StudentForm(db);
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

    private void showMarksEntry() {
        marksEntryForm = new MarksEntryForm(db);
        setContent(marksEntryForm.getView());
    }

    private void showAnalysis() {
        analysisForm = new AnalysisForm(db);
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
