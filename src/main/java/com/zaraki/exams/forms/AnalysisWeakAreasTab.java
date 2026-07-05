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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class AnalysisWeakAreasTab {

    private static final String[] CHART_COLORS = {AppTheme.RED, AppTheme.GREEN, AppTheme.WHITE_BG};

    private final IExamAnalysisService service;
    private final Stage stage;
    private final TableView<WeakAreaRow> table = new TableView<>();
    private BarChart<String, Number> chart;

    public AnalysisWeakAreasTab(IExamAnalysisService service, Stage stage) {
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
        chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Subjects Needing Improvement (Lowest \u2192 Highest)");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(250);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
            UIUtils.col("Subject", "subjectName", 200),
            UIUtils.col("Mean Score", "meanScore", 100),
            UIUtils.col("Grade", "grade", 70)
        );

        content.getChildren().addAll(chart, table);
        return content;
    }

    public void load(long examId) {
        Task<List<IExamAnalysisService.WeakArea>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.WeakArea> call() {
                return service.computeWeakAreas(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.WeakArea> weakAreas = task.getValue();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            ObservableList<WeakAreaRow> rows = FXCollections.observableArrayList();
            for (IExamAnalysisService.WeakArea wa : weakAreas) {
                series.getData().add(new XYChart.Data<>(wa.subjectName(), wa.meanScore()));
                rows.add(new WeakAreaRow(wa.subjectName(), wa.meanScore(), wa.grade()));
            }
            chart.getData().clear();
            chart.getData().add(series);
            styleBarSeries(series);
            table.setItems(rows);
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
