package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import static com.zaraki.exams.database.DatabaseEngine.validateFilterColumn;
import com.zaraki.exams.util.UIUtils;
import com.zaraki.exams.forms.PublishForm;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.zaraki.exams.reporting.ReportCardGenerator;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.sql.*;
import java.util.*;

public class AnalysisForm {

    private static final String PRIMARY = "#c62828";
    private static final String RED = "#c62828";
    private static final String GREEN = "#2e7d32";
    private static final String WHITE_BG = "#f5f5f5";
    private static final String[] CHART_COLORS = {RED, GREEN, WHITE_BG};

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;
    private final Stage stage;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private long currentExamId;

    // Summary labels (updated on compute)
    private final Label summaryMean = new Label("—");
    private final Label summaryStudents = new Label("—");
    private final Label summarySubjects = new Label("—");
    private final Label summaryPassRate = new Label("—");
    private final Label summaryHighest = new Label("—");
    private final Label summaryLowest = new Label("—");
    private final Label summaryBestSubj = new Label("—");
    private final Label summaryWorstSubj = new Label("—");

    public AnalysisForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Exam Analysis");

        UIUtils.loadExams(examBox);

        Button autoGradeBtn = new Button("Auto-Grade All");
        Button rankBtn = new Button("Compute Rankings");
        rankBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white; -fx-font-weight: bold;");
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox controls = new HBox(10);
        controls.getChildren().addAll(examBox, autoGradeBtn, rankBtn, spinner);

        // ───── Zeraki-style Summary Cards ─────
        HBox summaryCards = buildSummaryCards();

        TabPane tabs = new TabPane();

        Tab dashboardTab = buildDashboardTab();
        Tab classTab = buildClassRankingsTab();
        Tab subjectTab = buildSubjectMetricsTab();
        Tab gradeDistTab = buildGradeDistributionTab();
        Tab weakTab = buildWeakAreasTab();
        Tab trendTab = buildTrendTab();
        Tab comparisonTab = buildExamComparisonTab();
        Tab meritTab = buildMeritListTab();

        tabs.getTabs().addAll(dashboardTab, classTab, subjectTab, gradeDistTab, weakTab, trendTab, comparisonTab, meritTab);
        view.getChildren().addAll(header, controls, summaryCards, tabs);

        rankBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            currentExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!PublishForm.isExamReleased(currentExamId)) { UIUtils.showError("Exam not released by admin. Analysis unavailable."); return; }
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

    // ────────────────────── Summary Cards ──────────────────────

    private HBox buildSummaryCards() {
        HBox cards = new HBox(12);
        cards.setPadding(new Insets(5, 0, 5, 0));
        Node[] cardNodes = {
            summaryCard("Overall Mean", summaryMean, RED),
            summaryCard("Students", summaryStudents, GREEN),
            summaryCard("Subjects", summarySubjects, WHITE_BG),
            summaryCard("Pass Rate", summaryPassRate, RED),
            summaryCard("Highest", summaryHighest, GREEN),
            summaryCard("Lowest", summaryLowest, WHITE_BG),
            summaryCard("Best Subject", summaryBestSubj, RED),
            summaryCard("Needs Focus", summaryWorstSubj, GREEN),
        };
        cards.getChildren().addAll(cardNodes);
        return cards;
    }

    private VBox summaryCard(String title, Label valueLabel, String bgColor) {
        VBox card = new VBox(3);
        card.setPrefSize(130, 65);
        card.setAlignment(Pos.CENTER);
        boolean isWhite = WHITE_BG.equals(bgColor);
        String textColor = isWhite ? "#333333" : "white";
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6, 0, 0, 2);");
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("System", 10));
        titleLbl.setTextFill(isWhite ? Color.gray(0.4) : Color.web("white", 0.8));
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        valueLabel.setTextFill(Color.web(textColor));
        card.getChildren().addAll(valueLabel, titleLbl);
        return card;
    }

    // ────────────────────── Chart Color Helpers ──────────────────────

    private void styleBarSeries(XYChart.Series<?, ? extends Number> series) {
        int i = 0;
        for (var data : series.getData()) {
            String color = CHART_COLORS[i % CHART_COLORS.length];
            data.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) node.setStyle("-fx-bar-fill: " + color + ";");
            });
            i++;
        }
    }

    private void stylePieData(PieChart chart) {
        int i = 0;
        for (var data : chart.getData()) {
            String color = CHART_COLORS[i % CHART_COLORS.length];
            data.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
            });
            i++;
        }
    }

    // ────────────────────── Dashboard Tab ──────────────────────

    private Tab buildDashboardTab() {
        Tab tab = new Tab("Dashboard");
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        Label weakHeader = new Label("Weakest Areas (Lowest Mean Scores)");
        weakHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        BarChart<String, Number> weakChart = buildWeakAreasChart();
        weakChart.setPrefHeight(250);

        Label trendHeader = new Label("Class Performance Trend");
        trendHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        LineChart<Number, Number> trendChart = buildClassTrendChart();
        trendChart.setPrefHeight(250);

        content.getChildren().addAll(weakHeader, weakChart, trendHeader, trendChart);
        tab.setContent(content);
        return tab;
    }

    private BarChart<String, Number> weakAreasChart;
    private LineChart<Number, Number> classTrendChart;

    private BarChart<String, Number> buildWeakAreasChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Subject");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        weakAreasChart = new BarChart<>(xAxis, yAxis);
        weakAreasChart.setTitle("Subject Performance (Ascending)");
        weakAreasChart.setAnimated(false);
        weakAreasChart.setLegendVisible(false);
        return weakAreasChart;
    }

    private LineChart<Number, Number> buildClassTrendChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Exam #");
        xAxis.setTickUnit(1);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        yAxis.setForceZeroInRange(false);
        classTrendChart = new LineChart<>(xAxis, yAxis);
        classTrendChart.setTitle("Overall Mean Score per Exam");
        classTrendChart.setAnimated(false);
        classTrendChart.setLegendVisible(false);
        return classTrendChart;
    }

    // ────────────────────── Class Rankings Tab ──────────────────────

    private final TableView<StudentResultRow> classTable = new TableView<>();

    private Tab buildClassRankingsTab() {
        Tab tab = new Tab("Broadsheet");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudentResultRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("classRank")); cPos.setPrefWidth(45);

        TableColumn<StudentResultRow, Number> cPrevPos = new TableColumn<>("Prev Pos");
        cPrevPos.setCellValueFactory(new PropertyValueFactory<>("prevPosition")); cPrevPos.setPrefWidth(65);

        TableColumn<StudentResultRow, String> cPosChg = new TableColumn<>("\u0394 Pos");
        cPosChg.setCellValueFactory(new PropertyValueFactory<>("positionChangeDisplay")); cPosChg.setPrefWidth(55);

        classTable.getColumns().addAll(
            cPos, cPrevPos, cPosChg,
            UIUtils.col("Admission", "admissionNumber", 100),
            UIUtils.col("Name", "fullName", 170),
            UIUtils.col("Stream", "stream", 70),
            UIUtils.col("Marks", "totalMarks", 70),
            UIUtils.col("Pts", "totalPoints", 50),
            UIUtils.col("Prev Pts", "prevTotalMarks", 65),
            UIUtils.col("Mean", "meanPoints", 55),
            UIUtils.col("Grade", "meanGrade", 65),
            UIUtils.col("Out Of", "classSize", 55)
        );

        // Click student row → show weak areas dialog
        classTable.setRowFactory(tv -> {
            TableRow<StudentResultRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    StudentResultRow r = row.getItem();
                    showStudentWeakAreas(r.getStudentId(), r.getAdmissionNumber(), r.getFullName());
                }
            });
            return row;
        });

        content.getChildren().add(classTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Subject Metrics Tab (with BarChart) ──────────────────────

    private final TableView<SubjectMetricRow> subjectTable = new TableView<>();
    private BarChart<String, Number> subjectBarChart;

    private Tab buildSubjectMetricsTab() {
        Tab tab = new Tab("Subject Metrics");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Subject");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        subjectBarChart = new BarChart<>(xAxis, yAxis);
        subjectBarChart.setTitle("Subject Performance");
        subjectBarChart.setAnimated(false);
        subjectBarChart.setLegendVisible(false);
        subjectBarChart.setPrefHeight(250);

        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.getColumns().addAll(
            UIUtils.col("Rank", "rank", 50),
            UIUtils.col("Subject", "subjectName", 200),
            UIUtils.col("Dept", "department", 120),
            UIUtils.col("Mean Score", "meanScore", 100),
            UIUtils.col("Mean Grade", "meanGrade", 90),
            UIUtils.col("Std Dev", "stdDev", 90),
            UIUtils.col("Candidates", "candidates", 90)
        );

        content.getChildren().addAll(subjectBarChart, subjectTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Grade Distribution Tab (PieChart) ──────────────────────

    private PieChart gradePieChart;
    private final TableView<GradeDistRow> gradeDistTable = new TableView<>();

    private Tab buildGradeDistributionTab() {
        Tab tab = new Tab("Grade Distribution");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        gradePieChart = new PieChart();
        gradePieChart.setTitle("Overall Grade Distribution");
        gradePieChart.setAnimated(false);
        gradePieChart.setPrefHeight(300);
        gradePieChart.setLabelsVisible(true);

        gradeDistTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        content.getChildren().addAll(gradePieChart, gradeDistTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Weak Areas Tab ──────────────────────

    private final TableView<WeakAreaRow> weakTable = new TableView<>();
    private BarChart<String, Number> weakTabChart;

    private Tab buildWeakAreasTab() {
        Tab tab = new Tab("Weak Areas");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Subject");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        weakTabChart = new BarChart<>(xAxis, yAxis);
        weakTabChart.setTitle("Subjects Needing Improvement (Lowest → Highest)");
        weakTabChart.setAnimated(false);
        weakTabChart.setLegendVisible(false);
        weakTabChart.setPrefHeight(250);

        weakTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        weakTable.getColumns().addAll(
            UIUtils.col("Subject", "subjectName", 200),
            UIUtils.col("Mean Score", "meanScore", 100),
            UIUtils.col("Grade", "grade", 70)
        );

        content.getChildren().addAll(weakTabChart, weakTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Trend Tab ──────────────────────

    private LineChart<Number, Number> trendTabChart;

    private Tab buildTrendTab() {
        Tab tab = new Tab("Trends");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Exam");
        xAxis.setTickUnit(1);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        yAxis.setForceZeroInRange(false);
        trendTabChart = new LineChart<>(xAxis, yAxis);
        trendTabChart.setTitle("Performance Trend Across All Exams");
        trendTabChart.setAnimated(false);
        trendTabChart.setLegendVisible(false);
        trendTabChart.setPrefHeight(350);

        content.getChildren().add(trendTabChart);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Exam Comparison Tab ──────────────────────

    private final ComboBox<String> exam1Box = new ComboBox<>();
    private final ComboBox<String> exam2Box = new ComboBox<>();
    private final TableView<ExamComparisonRow> compareTable = new TableView<>();

    private Tab buildExamComparisonTab() {
        Tab tab = new Tab("Most Improv. / Dropped");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        UIUtils.loadExams(exam1Box);
        UIUtils.loadExams(exam2Box);
        exam1Box.setPromptText("Earlier Exam");
        exam2Box.setPromptText("Later Exam");

        Button compareBtn = new Button("Compare");
        HBox controls = new HBox(10, new Label("From:"), exam1Box, new Label("To:"), exam2Box, compareBtn);

        compareTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ExamComparisonRow, Number> cPosC = new TableColumn<>("#");
        cPosC.setCellValueFactory(new PropertyValueFactory<>("rank")); cPosC.setPrefWidth(50);
        TableColumn<ExamComparisonRow, String> cAdmC = new TableColumn<>("Admission");
        cAdmC.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdmC.setPrefWidth(100);
        TableColumn<ExamComparisonRow, String> cNameC = new TableColumn<>("Name");
        cNameC.setCellValueFactory(new PropertyValueFactory<>("fullName")); cNameC.setPrefWidth(160);
        TableColumn<ExamComparisonRow, String> cFormC = new TableColumn<>("Form");
        cFormC.setCellValueFactory(new PropertyValueFactory<>("form")); cFormC.setPrefWidth(55);
        TableColumn<ExamComparisonRow, String> cStreamC = new TableColumn<>("Stream");
        cStreamC.setCellValueFactory(new PropertyValueFactory<>("stream")); cStreamC.setPrefWidth(70);
        TableColumn<ExamComparisonRow, Number> cE1 = new TableColumn<>("Exam1 Pts");
        cE1.setCellValueFactory(new PropertyValueFactory<>("exam1Total")); cE1.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE1Pos = new TableColumn<>("Pos1");
        cE1Pos.setCellValueFactory(new PropertyValueFactory<>("exam1Pos")); cE1Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cE2 = new TableColumn<>("Exam2 Pts");
        cE2.setCellValueFactory(new PropertyValueFactory<>("exam2Total")); cE2.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE2Pos = new TableColumn<>("Pos2");
        cE2Pos.setCellValueFactory(new PropertyValueFactory<>("exam2Pos")); cE2Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cDiff = new TableColumn<>("\u0394 Pts");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difference")); cDiff.setPrefWidth(70);
        TableColumn<ExamComparisonRow, String> cPosDelta = new TableColumn<>("\u0394 Pos");
        cPosDelta.setCellValueFactory(new PropertyValueFactory<>("posChangeDisplay")); cPosDelta.setPrefWidth(60);
        compareTable.getColumns().addAll(cPosC, cAdmC, cNameC, cFormC, cStreamC, cE1, cE1Pos, cE2, cE2Pos, cDiff, cPosDelta);

        compareBtn.setOnAction(e -> {
            if (exam1Box.getValue() == null || exam2Box.getValue() == null) return;
            long e1 = Long.parseLong(exam1Box.getValue().split(" - ")[0]);
            long e2 = Long.parseLong(exam2Box.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<List<ExamAnalysisService.ExamComparison>> task = new Task<>() {
                @Override protected List<ExamAnalysisService.ExamComparison> call() {
                    return analysisService.compareExams(e1, e2);
                }
            };
            task.setOnSucceeded(ev -> {
                List<ExamAnalysisService.ExamComparison> list = task.getValue();
                ObservableList<ExamComparisonRow> rows = FXCollections.observableArrayList();
                int rank = 0;
                double prevDiff = Double.MAX_VALUE;
                for (int i = 0; i < list.size(); i++) {
                    ExamAnalysisService.ExamComparison ec = list.get(i);
                    if (ec.difference() < prevDiff) rank = i + 1;
                    prevDiff = ec.difference();
                    rows.add(new ExamComparisonRow(rank, ec.admissionNumber(), ec.fullName(),
                        ec.form(), ec.stream(),
                        ec.exam1Total(), ec.exam2Total(), ec.difference(),
                        ec.exam1Pos(), ec.exam2Pos(), ec.posChange()));
                }
                compareTable.setItems(rows);
                spinner.setVisible(false);
            });
            task.setOnFailed(ev -> { UIUtils.showError(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        content.getChildren().addAll(controls, compareTable);
        tab.setContent(content);
        return tab;
    }

    public static class ExamComparisonRow {
        private final int rank, exam1Pos, exam2Pos;
        private final String admissionNumber, fullName, form, stream;
        private final double exam1Total, exam2Total, difference;
        public ExamComparisonRow(int rank, String admissionNumber, String fullName,
                                 String form, String stream,
                                 double exam1Total, double exam2Total, double difference,
                                 int exam1Pos, int exam2Pos, int posChange) {
            this.rank = rank; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.form = form; this.stream = stream;
            this.exam1Total = exam1Total; this.exam2Total = exam2Total; this.difference = difference;
            this.exam1Pos = exam1Pos; this.exam2Pos = exam2Pos;
        }
        public int getRank() { return rank; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getForm() { return form; }
        public String getStream() { return stream; }
        public double getExam1Total() { return exam1Total; }
        public double getExam2Total() { return exam2Total; }
        public double getDifference() { return difference; }
        public int getExam1Pos() { return exam1Pos; }
        public int getExam2Pos() { return exam2Pos; }
        public String getPosChangeDisplay() {
            int ch = exam1Pos > 0 ? exam1Pos - exam2Pos : 0;
            if (ch > 0) return "+" + ch;
            if (ch < 0) return String.valueOf(ch);
            return "0";
        }
    }

    // ────────────────────── Merit List Tab ──────────────────────

    private final ComboBox<String> meritExamBox = new ComboBox<>();
    private final ComboBox<String> meritGroupBox = new ComboBox<>();
    private final ToggleGroup meritGroupType = new ToggleGroup();
    private final RadioButton meritStreamRb = new RadioButton("Stream");
    private final RadioButton meritFormRb = new RadioButton("Form");
    private final TableView<ExamAnalysisService.MeritStudent> meritTable = new TableView<>();

    private Tab buildMeritListTab() {
        Tab tab = new Tab("Merit List");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        UIUtils.loadExams(meritExamBox);
        HBox examRow = new HBox(10, new Label("Exam:"), meritExamBox);
        meritExamBox.setPrefWidth(300);

        HBox typeRow = new HBox(10);
        meritStreamRb.setToggleGroup(meritGroupType);
        meritFormRb.setToggleGroup(meritGroupType);
        meritStreamRb.setSelected(true);
        meritGroupBox.setPrefWidth(200);
        UIUtils.loadStreams(meritGroupBox);
        meritGroupType.selectedToggleProperty().addListener((obs, old, cur) -> {
            meritGroupBox.getItems().clear();
            if (cur == meritStreamRb) UIUtils.loadStreams(meritGroupBox);
            else UIUtils.loadForms(meritGroupBox);
        });
        typeRow.getChildren().addAll(new Label("Group By:"), meritStreamRb, meritFormRb, meritGroupBox);

        HBox btnRow = new HBox(10);
        Button showBtn = new Button("Show Merit List");
        Button exportPdfBtn = new Button("Export PDF");
        ProgressIndicator mSpinner = new ProgressIndicator();
        mSpinner.setVisible(false);
        mSpinner.setPrefSize(24, 24);
        btnRow.getChildren().addAll(showBtn, exportPdfBtn, mSpinner);

        meritTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        showBtn.setOnAction(e -> {
            if (meritExamBox.getValue() == null) { UIUtils.showError("Select exam."); return; }
            if (meritGroupBox.getValue() == null) { UIUtils.showError("Select group."); return; }
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            String groupValue = meritGroupBox.getValue();
            mSpinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    loadMeritTable(examId, groupBy, groupValue);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> mSpinner.setVisible(false));
            task.setOnFailed(ev -> { mSpinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        exportPdfBtn.setOnAction(e -> {
            if (meritExamBox.getValue() == null || meritGroupBox.getValue() == null) return;
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            String groupValue = meritGroupBox.getValue();
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Merit List");
            fc.setInitialFileName("merit_list_" + groupBy + "_" + groupValue.replace("/", "_") + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;
            mSpinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    new ReportCardGenerator().generateGroupReport(examId, groupBy, groupValue, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { mSpinner.setVisible(false); UIUtils.showInfo("Merit list PDF saved."); });
            task.setOnFailed(ev -> { mSpinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        content.getChildren().addAll(examRow, typeRow, btnRow, meritTable);
        tab.setContent(content);
        return tab;
    }

    private void loadMeritTable(long examId, String groupBy, String groupValue) {
        String filterCol = validateFilterColumn(groupBy.equals("stream") ? "stream" : "form");
        ExamAnalysisService.MeritReportData data;
        try {
            data = analysisService.computeMeritReport(examId, filterCol, groupValue);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
            return;
        }
        List<ExamAnalysisService.MeritSubject> subjects = data.subjects();
        List<ExamAnalysisService.MeritStudent> students = data.students();
        meritTable.getColumns().clear();
        TableColumn<ExamAnalysisService.MeritStudent, String> cAdm = new TableColumn<>("Adm");
        cAdm.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().admissionNumber()));
        cAdm.setPrefWidth(80);
        TableColumn<ExamAnalysisService.MeritStudent, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().fullName()));
        cName.setPrefWidth(140);
        meritTable.getColumns().addAll(cAdm, cName);
        for (ExamAnalysisService.MeritSubject si : subjects) {
            String label = si.code() != null && !si.code().isBlank() ? si.code() : si.name().substring(0, Math.min(4, si.name().length()));
            TableColumn<ExamAnalysisService.MeritStudent, String> parentCol = new TableColumn<>(label);
            long subjId = si.id();
            TableColumn<ExamAnalysisService.MeritStudent, Number> scrCol = new TableColumn<>("Scr");
            scrCol.setPrefWidth(45);
            scrCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().scores().getOrDefault(subjId, 0.0)));
            TableColumn<ExamAnalysisService.MeritStudent, Number> posCol = new TableColumn<>("Pos");
            posCol.setPrefWidth(35);
            posCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().subjectPositions().getOrDefault(subjId, 0)));
            parentCol.getColumns().addAll(scrCol, posCol);
            meritTable.getColumns().add(parentCol);
        }
        TableColumn<ExamAnalysisService.MeritStudent, Number> cDeviation = new TableColumn<>("Dev");
        cDeviation.setPrefWidth(50);
        cDeviation.setCellValueFactory(cd -> {
            var devs = cd.getValue().deviations();
            double avg = devs.isEmpty() ? 0 : devs.values().stream().mapToDouble(v -> v).average().orElse(0);
            return new SimpleObjectProperty<>(Math.round(avg * 10.0) / 10.0);
        });
        TableColumn<ExamAnalysisService.MeritStudent, Number> cMarks = new TableColumn<>("T.Mks");
        cMarks.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().totalMarks()));
        cMarks.setPrefWidth(55);
        TableColumn<ExamAnalysisService.MeritStudent, Number> cPos = new TableColumn<>("Pos");
        cPos.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().rank()));
        cPos.setPrefWidth(40);
        TableColumn<ExamAnalysisService.MeritStudent, Number> cMean = new TableColumn<>("Mean");
        cMean.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().meanPoints()));
        cMean.setPrefWidth(50);
        TableColumn<ExamAnalysisService.MeritStudent, String> cGrade = new TableColumn<>("Gr");
        cGrade.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().meanGrade()));
        cGrade.setPrefWidth(40);
        meritTable.getColumns().addAll(cDeviation, cMarks, cPos, cMean, cGrade);
        meritTable.setItems(FXCollections.observableArrayList(students));
    }

    // ────────────────────── Compute All Tabs ──────────────────────

    private void computeAllTabs(long examId) {
        // Load summary
        Task<ExamAnalysisService.ExamSummary> summaryTask = new Task<>() {
            @Override protected ExamAnalysisService.ExamSummary call() {
                return analysisService.computeExamSummary(examId);
            }
        };
        summaryTask.setOnSucceeded(ev -> {
            ExamAnalysisService.ExamSummary s = summaryTask.getValue();
            summaryMean.setText(String.format("%.1f", s.overallMean()));
            summaryStudents.setText(String.valueOf(s.totalStudents()));
            summarySubjects.setText(String.valueOf(s.totalSubjects()));
            summaryPassRate.setText(String.format("%.1f%%", s.passRate()));
            summaryHighest.setText(String.format("%.1f", s.highestScore()));
            summaryLowest.setText(String.format("%.1f", s.lowestScore()));
            summaryBestSubj.setText(s.bestSubject());
            summaryWorstSubj.setText(s.worstSubject());
        });
        summaryTask.setOnFailed(ev -> { /* ignore summary failures */ });
        new Thread(summaryTask).start();

        // Load weak areas chart & table
        Task<List<ExamAnalysisService.WeakArea>> weakTask = new Task<>() {
            @Override protected List<ExamAnalysisService.WeakArea> call() {
                return analysisService.computeWeakAreas(examId);
            }
        };
        weakTask.setOnSucceeded(ev -> {
            List<ExamAnalysisService.WeakArea> weakAreas = weakTask.getValue();

            // Dashboard weak chart
            XYChart.Series<String, Number> weakSeries = new XYChart.Series<>();
            for (ExamAnalysisService.WeakArea wa : weakAreas)
                weakSeries.getData().add(new XYChart.Data<>(wa.subjectName(), wa.meanScore()));
            weakAreasChart.getData().clear();
            weakAreasChart.getData().add(weakSeries);
            styleBarSeries(weakSeries);

            // Weak tab chart & table
            XYChart.Series<String, Number> weakTabSeries = new XYChart.Series<>();
            ObservableList<WeakAreaRow> weakRows = FXCollections.observableArrayList();
            for (ExamAnalysisService.WeakArea wa : weakAreas) {
                weakTabSeries.getData().add(new XYChart.Data<>(wa.subjectName(), wa.meanScore()));
                weakRows.add(new WeakAreaRow(wa.subjectName(), wa.meanScore(), wa.grade()));
            }
            weakTabChart.getData().clear();
            weakTabChart.getData().add(weakTabSeries);
            styleBarSeries(weakTabSeries);
            weakTable.setItems(weakRows);
        });
        weakTask.setOnFailed(ev -> { /* ignore */ });
        new Thread(weakTask).start();

        // Load class trends
        Task<List<ExamAnalysisService.ClassTrend>> trendTask = new Task<>() {
            @Override protected List<ExamAnalysisService.ClassTrend> call() {
                return analysisService.computeClassTrends();
            }
        };
        trendTask.setOnSucceeded(ev -> {
            List<ExamAnalysisService.ClassTrend> trends = trendTask.getValue();

            // Dashboard trend chart
            XYChart.Series<Number, Number> dashSeries = new XYChart.Series<>();
            for (int i = 0; i < trends.size(); i++)
                dashSeries.getData().add(new XYChart.Data<>(i + 1, trends.get(i).meanScore()));
            classTrendChart.getData().clear();
            classTrendChart.getData().add(dashSeries);

            // Trend tab chart
            XYChart.Series<Number, Number> tabSeries = new XYChart.Series<>();
            tabSeries.setName("Mean Score");
            for (int i = 0; i < trends.size(); i++)
                tabSeries.getData().add(new XYChart.Data<>(i + 1, trends.get(i).meanScore()));
            trendTabChart.getData().clear();
            trendTabChart.getData().add(tabSeries);

            // Auto-range Y axis
            if (!trends.isEmpty()) {
                double mn = trends.stream().mapToDouble(ExamAnalysisService.ClassTrend::meanScore).min().orElse(0);
                double mx = trends.stream().mapToDouble(ExamAnalysisService.ClassTrend::meanScore).max().orElse(100);
                double pad = Math.max((mx - mn) * 0.2, 5);
                NumberAxis yAxis = (NumberAxis) trendTabChart.getYAxis();
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(Math.max(0, mn - pad));
                yAxis.setUpperBound(Math.min(100, mx + pad));
                yAxis.setTickUnit(Math.max(1, (mx - mn + 2 * pad) / 8));
            }
        });
        trendTask.setOnFailed(ev -> { /* ignore */ });
        new Thread(trendTask).start();

        // Grade distribution pie chart
        Task<List<ExamAnalysisService.GradeDistribution>> gradeTask = new Task<>() {
            @Override protected List<ExamAnalysisService.GradeDistribution> call() {
                return analysisService.computeGradeDistribution(examId);
            }
        };
        gradeTask.setOnSucceeded(ev -> {
            List<ExamAnalysisService.GradeDistribution> gradeData = gradeTask.getValue();

            // Pie chart: aggregate all subjects
            Map<String, Integer> overallGrades = new LinkedHashMap<>();
            for (var gd : gradeData) {
                for (var entry : gd.gradeCounts().entrySet())
                    overallGrades.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (var entry : overallGrades.entrySet())
                pieData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
            gradePieChart.setData(pieData);
            stylePieData(gradePieChart);

            // Table
            gradeDistTable.getColumns().clear();
            Set<String> allGrades = new LinkedHashSet<>();
            for (var gd : gradeData) allGrades.addAll(gd.gradeCounts().keySet());
            List<String> gradeOrder = new ArrayList<>(allGrades);

            TableColumn<GradeDistRow, String> cSubject = new TableColumn<>("Subject");
            cSubject.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
            cSubject.setPrefWidth(200);
            gradeDistTable.getColumns().add(cSubject);

            for (String g : gradeOrder) {
                TableColumn<GradeDistRow, Number> col = new TableColumn<>(g);
                col.setPrefWidth(50);
                final String grade = g;
                col.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().gradeCounts.getOrDefault(grade, 0)));
                gradeDistTable.getColumns().add(col);
            }
            ObservableList<GradeDistRow> rows = FXCollections.observableArrayList();
            for (var gd : gradeData) rows.add(new GradeDistRow(gd.subjectName(), gd.gradeCounts()));
            gradeDistTable.setItems(rows);
        });
        gradeTask.setOnFailed(ev -> { /* ignore */ });
        new Thread(gradeTask).start();

        // Subject metrics + bar chart
        Task<List<ExamAnalysisService.SubjectMetrics>> subTask = new Task<>() {
            @Override protected List<ExamAnalysisService.SubjectMetrics> call() {
                return analysisService.computeSubjectMetrics(examId);
            }
        };
        subTask.setOnSucceeded(ev2 -> {
            List<ExamAnalysisService.SubjectMetrics> mResults = subTask.getValue();
            ObservableList<SubjectMetricRow> sRows = FXCollections.observableArrayList();
            XYChart.Series<String, Number> barSeries = new XYChart.Series<>();
            for (ExamAnalysisService.SubjectMetrics r : mResults) {
                sRows.add(new SubjectMetricRow(r.subjectId(), r.subjectName(), r.department(),
                    r.meanScore(), r.meanGrade(), r.stdDev(), r.subjectRank(), r.totalCandidates()));
                barSeries.getData().add(new XYChart.Data<>(r.subjectName().length() > 8 ? r.subjectName().substring(0, 8) : r.subjectName(), r.meanScore()));
            }
            subjectTable.setItems(sRows);
            subjectBarChart.getData().clear();
            subjectBarChart.getData().add(barSeries);
            styleBarSeries(barSeries);
            spinner.setVisible(false);
        });
        subTask.setOnFailed(ev2 -> { UIUtils.showError(subTask.getException().getMessage()); spinner.setVisible(false); });
        new Thread(subTask).start();

        // Class rankings (broadsheet)
        Task<List<ExamAnalysisService.StudentResult>> mainTask = new Task<>() {
            @Override protected List<ExamAnalysisService.StudentResult> call() {
                return analysisService.computeClassRankings(examId);
            }
        };
        mainTask.setOnSucceeded(ev -> {
            List<ExamAnalysisService.StudentResult> results = mainTask.getValue();
            long prevExamId = analysisService.findPreviousExam(examId);
            Map<Long, Double> prevTotals = prevExamId > 0 ? analysisService.getExamStudentTotals(prevExamId) : Collections.emptyMap();
            Map<Long, Integer> prevRanks = prevExamId > 0 ? analysisService.getExamStudentRanks(prevExamId) : Collections.emptyMap();
            ObservableList<StudentResultRow> rows = FXCollections.observableArrayList();
            for (ExamAnalysisService.StudentResult r : results) {
                double prevMarks = prevTotals.getOrDefault(r.studentId(), 0.0);
                int prevPos = prevRanks.getOrDefault(r.studentId(), 0);
                int change = prevPos > 0 ? prevPos - r.classRank() : 0;
                rows.add(new StudentResultRow(r.studentId(), r.admissionNumber(), r.fullName(),
                    r.form(), r.stream(), r.totalMarks(), r.totalPoints(), r.meanPoints(),
                    r.meanGrade(), r.classRank(), r.classSize(), prevMarks, prevPos, change));
            }
            classTable.setItems(rows);
        });
        mainTask.setOnFailed(ev -> { UIUtils.showError(mainTask.getException().getMessage()); spinner.setVisible(false); });
        new Thread(mainTask).start();
    }

    // ────────────────────── Student Weak Areas Dialog ──────────────────────

    private void showStudentWeakAreas(long studentId, String admission, String name) {
        if (currentExamId == 0) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Weak Areas — " + name);
        dialog.setHeaderText(name + " (" + admission + ") — Subjects sorted by weakest score");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        ProgressIndicator spin = new ProgressIndicator();
        spin.setPrefSize(20, 20);
        content.getChildren().add(spin);
        dialog.getDialogPane().setContent(content);

        Task<List<ExamAnalysisService.StudentWeakArea>> task = new Task<>() {
            @Override protected List<ExamAnalysisService.StudentWeakArea> call() {
                return analysisService.computeStudentWeakAreas(currentExamId, studentId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<ExamAnalysisService.StudentWeakArea> areas = task.getValue();
            content.getChildren().clear();

            TableView<ExamAnalysisService.StudentWeakArea> table = new TableView<>();
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.getColumns().addAll(
                UIUtils.col("Subject", "subjectName", 160),
                UIUtils.col("Score", "score", 70),
                UIUtils.col("Grade", "grade", 60),
                UIUtils.col("Class Avg", "classMean", 80),
                UIUtils.col("Deviation", "deviation", 80)
            );
            table.setItems(FXCollections.observableArrayList(areas));
            table.setPrefHeight(300);
            content.getChildren().add(table);
        });
        task.setOnFailed(ev -> {
            content.getChildren().clear();
            content.getChildren().add(new Label("Error: " + task.getException().getMessage()));
        });
        new Thread(task).start();
        dialog.showAndWait();
    }

    // ────────────────────── Row Classes ──────────────────────

    public static class StudentResultRow {
        private final long studentId;
        private final String admissionNumber, fullName, form, stream, meanGrade;
        private final double totalMarks, meanPoints, prevTotalMarks;
        private final int totalPoints, classRank, classSize, prevPosition, positionChange;

        public StudentResultRow(long studentId, String admissionNumber, String fullName,
                                String form, String stream, double totalMarks, int totalPoints,
                                double meanPoints, String meanGrade, int classRank, int classSize,
                                double prevTotalMarks, int prevPosition, int positionChange) {
            this.studentId = studentId; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.form = form; this.stream = stream; this.totalMarks = totalMarks; this.totalPoints = totalPoints;
            this.meanPoints = meanPoints; this.meanGrade = meanGrade; this.classRank = classRank;
            this.classSize = classSize;
            this.prevTotalMarks = prevTotalMarks; this.prevPosition = prevPosition; this.positionChange = positionChange;
        }
        public long getStudentId() { return studentId; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getForm() { return form; }
        public String getStream() { return stream; }
        public double getTotalMarks() { return totalMarks; }
        public int getTotalPoints() { return totalPoints; }
        public double getMeanPoints() { return meanPoints; }
        public String getMeanGrade() { return meanGrade; }
        public int getClassRank() { return classRank; }
        public int getClassSize() { return classSize; }
        public double getPrevTotalMarks() { return prevTotalMarks; }
        public int getPrevPosition() { return prevPosition; }
        public int getPositionChange() { return positionChange; }
        public String getPositionChangeDisplay() {
            if (positionChange > 0) return "+" + positionChange;
            if (positionChange < 0) return String.valueOf(positionChange);
            return "0";
        }
    }

    public static class SubjectMetricRow {
        private final long subjectId; private final String subjectName, department;
        private final double meanScore, stdDev; private final String meanGrade;
        private final int rank, candidates;
        public SubjectMetricRow(long subjectId, String subjectName, String department,
                                double meanScore, String meanGrade, double stdDev,
                                int rank, int candidates) {
            this.subjectId = subjectId; this.subjectName = subjectName; this.department = department;
            this.meanScore = meanScore; this.meanGrade = meanGrade; this.stdDev = stdDev;
            this.rank = rank; this.candidates = candidates;
        }
        public long getSubjectId() { return subjectId; }
        public String getSubjectName() { return subjectName; }
        public String getDepartment() { return department; }
        public double getMeanScore() { return meanScore; }
        public String getMeanGrade() { return meanGrade; }
        public double getStdDev() { return stdDev; }
        public int getRank() { return rank; }
        public int getCandidates() { return candidates; }
    }

    public static class GradeDistRow {
        private final String subjectName;
        private final Map<String, Integer> gradeCounts;
        public GradeDistRow(String subjectName, Map<String, Integer> gradeCounts) {
            this.subjectName = subjectName;
            this.gradeCounts = gradeCounts;
        }
        public String getSubjectName() { return subjectName; }
    }

    public static class WeakAreaRow {
        private final String subjectName, grade;
        private final double meanScore;
        public WeakAreaRow(String subjectName, double meanScore, String grade) {
            this.subjectName = subjectName; this.meanScore = meanScore; this.grade = grade;
        }
        public String getSubjectName() { return subjectName; }
        public double getMeanScore() { return meanScore; }
        public String getGrade() { return grade; }
    }
}
