package com.zaraki.exams.forms;

import com.zaraki.exams.service.IExamAnalysisService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class AnalysisTrendTab {

    private final IExamAnalysisService service;
    private final Stage stage;
    private final LineChart<Number, Number> chart;

    public AnalysisTrendTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
        this.chart = buildChart();
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        chart.setPrefHeight(350);
        content.getChildren().add(chart);
        return content;
    }

    public void load(long examId) {
        Task<List<IExamAnalysisService.ClassTrend>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.ClassTrend> call() {
                return service.computeClassTrends();
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.ClassTrend> trends = task.getValue();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Mean Score");
            for (int i = 0; i < trends.size(); i++)
                series.getData().add(new XYChart.Data<>(i + 1, trends.get(i).meanScore()));
            chart.getData().clear();
            chart.getData().add(series);

            if (!trends.isEmpty()) {
                double mn = trends.stream().mapToDouble(IExamAnalysisService.ClassTrend::meanScore).min().orElse(0);
                double mx = trends.stream().mapToDouble(IExamAnalysisService.ClassTrend::meanScore).max().orElse(100);
                double pad = Math.max((mx - mn) * 0.2, 5);
                NumberAxis yAxis = (NumberAxis) chart.getYAxis();
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(Math.max(0, mn - pad));
                yAxis.setUpperBound(Math.min(100, mx + pad));
                yAxis.setTickUnit(Math.max(1, (mx - mn + 2 * pad) / 8));
            }
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

    private LineChart<Number, Number> buildChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Exam");
        xAxis.setTickUnit(1);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> c = new LineChart<>(xAxis, yAxis);
        c.setTitle("Performance Trend Across All Exams");
        c.setAnimated(false);
        c.setLegendVisible(false);
        return c;
    }
}
