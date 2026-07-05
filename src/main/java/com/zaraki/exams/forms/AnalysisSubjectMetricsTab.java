package com.zaraki.exams.forms;

import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class AnalysisSubjectMetricsTab {

    private static final String[] CHART_COLORS = {AppTheme.RED, AppTheme.GREEN, AppTheme.WHITE_BG};

    private final IExamAnalysisService service;
    private final Stage stage;
    private final TableView<SubjectMetricRow> table = new TableView<>();
    private BarChart<String, Number> barChart;

    public AnalysisSubjectMetricsTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Subject");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Subject Performance");
        barChart.setAnimated(false);
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(250);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
            UIUtils.col("Rank", "rank", 50),
            UIUtils.col("Subject", "subjectName", 200),
            UIUtils.col("Dept", "department", 120),
            UIUtils.col("Mean Score", "meanScore", 100),
            UIUtils.col("Mean Grade", "meanGrade", 90),
            UIUtils.col("Std Dev", "stdDev", 90),
            UIUtils.col("Candidates", "candidates", 90)
        );

        content.getChildren().addAll(barChart, table);
        return content;
    }

    public void load(long examId) {
        Task<List<IExamAnalysisService.SubjectMetrics>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.SubjectMetrics> call() {
                return service.computeSubjectMetrics(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.SubjectMetrics> results = task.getValue();
            ObservableList<SubjectMetricRow> rows = FXCollections.observableArrayList();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (IExamAnalysisService.SubjectMetrics r : results) {
                rows.add(new SubjectMetricRow(r.subjectId(), r.subjectName(), r.department(),
                    r.meanScore(), r.meanGrade(), r.stdDev(), r.subjectRank(), r.totalCandidates()));
                series.getData().add(new XYChart.Data<>(r.subjectName().length() > 8 ? r.subjectName().substring(0, 8) : r.subjectName(), r.meanScore()));
            }
            table.setItems(rows);
            barChart.getData().clear();
            barChart.getData().add(series);
            styleBarSeries(series);
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

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
