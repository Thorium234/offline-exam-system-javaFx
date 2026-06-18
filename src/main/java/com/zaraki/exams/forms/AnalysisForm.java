package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnalysisForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final ProgressIndicator spinner = new ProgressIndicator();

    public AnalysisForm(DatabaseEngine db) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Exam Analysis");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        loadExams(examBox);

        Button autoGradeBtn = new Button("Auto-Grade All");
        Button rankBtn = new Button("Compute Rankings");
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox controls = new HBox(10);
        controls.getChildren().addAll(examBox, autoGradeBtn, rankBtn, spinner);

        TabPane tabs = new TabPane();

        Tab classTab = buildClassRankingsTab();
        Tab subjectTab = buildSubjectMetricsTab();
        Tab gradeDistTab = buildGradeDistributionTab();
        Tab comparisonTab = buildExamComparisonTab();

        tabs.getTabs().addAll(classTab, subjectTab, gradeDistTab, comparisonTab);
        view.getChildren().addAll(header, controls, tabs);

        rankBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            computeAllTabs(examId);
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
            task.setOnSucceeded(ev -> { showInfo("Auto-grading complete."); spinner.setVisible(false); });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        return view;
    }

    // ────────────────────── Class Rankings Tab ──────────────────────

    private final TableView<StudentResultRow> classTable = new TableView<>();

    private Tab buildClassRankingsTab() {
        Tab tab = new Tab("Class Rankings");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        classTable.getColumns().addAll(
            col("#", "classRank", 40),
            col("Admission", "admissionNumber", 110),
            col("Name", "fullName", 180),
            col("Stream", "stream", 80),
            col("Marks", "totalMarks", 70),
            col("Pts", "totalPoints", 50),
            col("Mean", "meanPoints", 60),
            col("Grade", "meanGrade", 70),
            col("Out Of", "classSize", 60)
        );

        TableColumn<StudentResultRow, Number> cPrevMarks = new TableColumn<>("Prev Marks");
        cPrevMarks.setCellValueFactory(new PropertyValueFactory<>("prevTotalMarks"));
        cPrevMarks.setPrefWidth(80);

        TableColumn<StudentResultRow, String> cPosChange = new TableColumn<>("+/-");
        cPosChange.setCellValueFactory(new PropertyValueFactory<>("positionChangeDisplay"));
        cPosChange.setPrefWidth(60);

        classTable.getColumns().addAll(cPrevMarks, cPosChange);
        content.getChildren().add(classTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Subject Metrics Tab ──────────────────────

    private final TableView<SubjectMetricRow> subjectTable = new TableView<>();

    private Tab buildSubjectMetricsTab() {
        Tab tab = new Tab("Subject Metrics");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.getColumns().addAll(
            col("Rank", "rank", 50),
            col("Subject", "subjectName", 200),
            col("Dept", "department", 120),
            col("Mean Score", "meanScore", 100),
            col("Mean Grade", "meanGrade", 90),
            col("Std Dev", "stdDev", 90),
            col("Candidates", "candidates", 90)
        );
        content.getChildren().add(subjectTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Grade Distribution Tab ──────────────────────

    private final TableView<GradeDistRow> gradeDistTable = new TableView<>();

    private Tab buildGradeDistributionTab() {
        Tab tab = new Tab("Grade Distribution");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        gradeDistTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        content.getChildren().add(gradeDistTable);
        tab.setContent(content);
        return tab;
    }

    private void populateGradeDistribution(long examId) {
        gradeDistTable.getColumns().clear();
        Task<List<ExamAnalysisService.GradeDistribution>> task = new Task<>() {
            @Override protected List<ExamAnalysisService.GradeDistribution> call() {
                return analysisService.computeGradeDistribution(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<ExamAnalysisService.GradeDistribution> data = task.getValue();

            Set<String> allGrades = new LinkedHashSet<>();
            for (var gd : data) allGrades.addAll(gd.gradeCounts().keySet());
            List<String> gradeOrder = new ArrayList<>(allGrades);

            TableColumn<GradeDistRow, String> cSubject = new TableColumn<>("Subject");
            cSubject.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
            cSubject.setPrefWidth(200);
            gradeDistTable.getColumns().add(cSubject);

            Map<String, TableColumn<GradeDistRow, Number>> gradeCols = new LinkedHashMap<>();
            for (String g : gradeOrder) {
                TableColumn<GradeDistRow, Number> col = new TableColumn<>(g);
                col.setPrefWidth(50);
                final String grade = g;
                col.setCellValueFactory(cd -> {
                    GradeDistRow row = cd.getValue();
                    return new SimpleObjectProperty<>(row.gradeCounts.getOrDefault(grade, 0));
                });
                gradeDistTable.getColumns().add(col);
                gradeCols.put(g, col);
            }

            ObservableList<GradeDistRow> rows = FXCollections.observableArrayList();
            for (var gd : data) rows.add(new GradeDistRow(gd.subjectName(), gd.gradeCounts()));
            gradeDistTable.setItems(rows);
        });
        task.setOnFailed(ev -> showAlert(task.getException().getMessage()));
        new Thread(task).start();
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

    // ────────────────────── Exam Comparison Tab ──────────────────────

    private final ComboBox<String> exam1Box = new ComboBox<>();
    private final ComboBox<String> exam2Box = new ComboBox<>();
    private final TableView<ExamComparisonRow> compareTable = new TableView<>();

    private Tab buildExamComparisonTab() {
        Tab tab = new Tab("Most Improved / Dropped");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        loadExams(exam1Box);
        loadExams(exam2Box);
        exam1Box.setPromptText("Earlier Exam");
        exam2Box.setPromptText("Later Exam");

        Button compareBtn = new Button("Compare");
        HBox controls = new HBox(10, new Label("From:"), exam1Box, new Label("To:"), exam2Box, compareBtn);

        compareTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ExamComparisonRow, Number> cPosC = new TableColumn<>("#");
        cPosC.setCellValueFactory(new PropertyValueFactory<>("rank")); cPosC.setPrefWidth(50);
        TableColumn<ExamComparisonRow, String> cAdmC = new TableColumn<>("Admission");
        cAdmC.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdmC.setPrefWidth(110);
        TableColumn<ExamComparisonRow, String> cNameC = new TableColumn<>("Name");
        cNameC.setCellValueFactory(new PropertyValueFactory<>("fullName")); cNameC.setPrefWidth(180);
        TableColumn<ExamComparisonRow, Number> cE1 = new TableColumn<>("Exam 1");
        cE1.setCellValueFactory(new PropertyValueFactory<>("exam1Total")); cE1.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE2 = new TableColumn<>("Exam 2");
        cE2.setCellValueFactory(new PropertyValueFactory<>("exam2Total")); cE2.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cDiff = new TableColumn<>("Change");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difference")); cDiff.setPrefWidth(80);
        compareTable.getColumns().addAll(cPosC, cAdmC, cNameC, cE1, cE2, cDiff);

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
                        ec.exam1Total(), ec.exam2Total(), ec.difference()));
                }
                compareTable.setItems(rows);
                spinner.setVisible(false);
            });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        content.getChildren().addAll(controls, compareTable);
        tab.setContent(content);
        return tab;
    }

    public static class ExamComparisonRow {
        private final int rank;
        private final String admissionNumber, fullName;
        private final double exam1Total, exam2Total, difference;
        public ExamComparisonRow(int rank, String admissionNumber, String fullName,
                                 double exam1Total, double exam2Total, double difference) {
            this.rank = rank; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.exam1Total = exam1Total; this.exam2Total = exam2Total; this.difference = difference;
        }
        public int getRank() { return rank; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public double getExam1Total() { return exam1Total; }
        public double getExam2Total() { return exam2Total; }
        public double getDifference() { return difference; }
    }

    // ────────────────────── Compute All Tabs ──────────────────────

    private void computeAllTabs(long examId) {
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

            Task<List<ExamAnalysisService.SubjectMetrics>> subTask = new Task<>() {
                @Override protected List<ExamAnalysisService.SubjectMetrics> call() {
                    return analysisService.computeSubjectMetrics(examId);
                }
            };
            subTask.setOnSucceeded(ev2 -> {
                List<ExamAnalysisService.SubjectMetrics> mResults = subTask.getValue();
                ObservableList<SubjectMetricRow> sRows = FXCollections.observableArrayList();
                for (ExamAnalysisService.SubjectMetrics r : mResults)
                    sRows.add(new SubjectMetricRow(r.subjectId(), r.subjectName(), r.department(),
                        r.meanScore(), r.meanGrade(), r.stdDev(), r.subjectRank(), r.totalCandidates()));
                subjectTable.setItems(sRows);
                spinner.setVisible(false);
            });
            subTask.setOnFailed(ev2 -> { showAlert(subTask.getException().getMessage()); spinner.setVisible(false); });
            new Thread(subTask).start();

            populateGradeDistribution(examId);
        });
        mainTask.setOnFailed(ev -> { showAlert(mainTask.getException().getMessage()); spinner.setVisible(false); });
        new Thread(mainTask).start();
    }

    // ────────────────────── Helpers ──────────────────────

    private void loadExams(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private static <T> TableColumn<T, ?> col(String title, String prop, int width) {
        TableColumn<T, ?> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    private void showAlert(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait()); }
    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait()); }

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
}
