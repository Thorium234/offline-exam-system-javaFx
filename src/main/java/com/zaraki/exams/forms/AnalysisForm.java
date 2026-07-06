package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.service.ExamAnalysisServiceImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class AnalysisForm {

    private final DatabaseEngine db;
    private final IExamAnalysisService analysisService;
    private final Stage stage;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private long currentExamId;

    private final AnalysisDashboardTab dashboardTab;
    private final AnalysisBroadsheetTab broadsheetTab;
    private final AnalysisSubjectMetricsTab subjectMetricsTab;
    private final AnalysisGradeDistTab gradeDistTab;
    private final AnalysisWeakAreasTab weakAreasTab;
    private final AnalysisTrendTab trendTab;
    private final AnalysisComparisonTab comparisonTab;
    private final AnalysisMeritListTab meritListTab;

    public AnalysisForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.analysisService = new ExamAnalysisServiceImpl();
        this.dashboardTab = new AnalysisDashboardTab(analysisService, stage);
        this.broadsheetTab = new AnalysisBroadsheetTab(analysisService, stage);
        this.subjectMetricsTab = new AnalysisSubjectMetricsTab(analysisService, stage);
        this.gradeDistTab = new AnalysisGradeDistTab(analysisService, stage);
        this.weakAreasTab = new AnalysisWeakAreasTab(analysisService, stage);
        this.trendTab = new AnalysisTrendTab(analysisService, stage);
        this.comparisonTab = new AnalysisComparisonTab(analysisService, stage);
        this.meritListTab = new AnalysisMeritListTab(analysisService, stage);
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Exam Analysis");

        UIUtils.loadExams(examBox);

        if (examBox.getItems().isEmpty()) {
            Label empty = new Label("No exams found. Create an exam and add marks to view analysis.");
            empty.setFont(javafx.scene.text.Font.font("System", 14));
            empty.setTextFill(javafx.scene.paint.Color.gray(0.5));
            view.getChildren().addAll(header, empty);
            return view;
        }

        Button autoGradeBtn = new Button("Auto-Grade All");
        Button rankBtn = new Button("Compute Rankings");
        rankBtn.setStyle("-fx-background-color: " + AppTheme.PRIMARY + "; -fx-text-fill: white; -fx-font-weight: bold;");
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox controls = new HBox(10, examBox, autoGradeBtn, rankBtn, spinner);

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
            new Tab("Dashboard", dashboardTab.getView()),
            new Tab("Broadsheet", broadsheetTab.getView()),
            new Tab("Subject Metrics", subjectMetricsTab.getView()),
            new Tab("Grade Distribution", gradeDistTab.getView()),
            new Tab("Weak Areas", weakAreasTab.getView()),
            new Tab("Trends", trendTab.getView()),
            new Tab("Most Improv. / Dropped", comparisonTab.getView()),
            new Tab("Merit List", meritListTab.getView())
        );

        view.getChildren().addAll(header, controls, tabs);

        rankBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            currentExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!PublishForm.isExamReleased(currentExamId)) {
                UIUtils.showError("Exam not released by admin. Analysis unavailable.");
                return;
            }
            spinner.setVisible(true);
            computeAllTabs(currentExamId);
        });

        autoGradeBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    analysisService.autoGradeExam(examId);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { UIUtils.showInfo("Auto-grading complete."); spinner.setVisible(false); });
            task.setOnFailed(ev -> { UIUtils.showError(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        return view;
    }

    private void computeAllTabs(long examId) {
        dashboardTab.load(examId);
        broadsheetTab.setCurrentExamId(examId);
        broadsheetTab.load(examId);
        subjectMetricsTab.load(examId);
        gradeDistTab.load(examId);
        weakAreasTab.load(examId);
        trendTab.load(examId);
        comparisonTab.load(examId);
        meritListTab.load(examId);
        spinner.setVisible(false);
    }
}
