package com.zaraki.exams.forms;

import com.zaraki.exams.service.IExamAnalysisService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;

public class AnalysisDashboardTab {

    private static final String[] CHART_COLORS = {AppTheme.RED, AppTheme.GREEN, AppTheme.WHITE_BG};

    private final IExamAnalysisService service;
    private final Stage stage;

    private final BarChart<String, Number> weakAreasChart;
    private final LineChart<Number, Number> classTrendChart;
    private final Label summaryMean = new Label("\u2014");
    private final Label summaryStudents = new Label("\u2014");
    private final Label summarySubjects = new Label("\u2014");
    private final Label summaryPassRate = new Label("\u2014");
    private final Label summaryHighest = new Label("\u2014");
    private final Label summaryLowest = new Label("\u2014");
    private final Label summaryBestSubj = new Label("\u2014");
    private final Label summaryWorstSubj = new Label("\u2014");

    public AnalysisDashboardTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
        this.weakAreasChart = buildWeakAreasChart();
        this.classTrendChart = buildClassTrendChart();
    }

    public Node getView() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        HBox summaryCards = buildSummaryCards();
        content.getChildren().add(summaryCards);

        Label weakHeader = new Label("Weakest Areas (Lowest Mean Scores)");
        weakHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        weakAreasChart.setPrefHeight(250);

        Label trendHeader = new Label("Class Performance Trend");
        trendHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        classTrendChart.setPrefHeight(250);

        content.getChildren().addAll(weakHeader, weakAreasChart, trendHeader, classTrendChart);
        return content;
    }

    public void load(long examId) {
        loadSummary(examId);
        loadWeakAreas(examId);
        loadTrend();
    }

    private void loadSummary(long examId) {
        Task<IExamAnalysisService.ExamSummary> task = new Task<>() {
            @Override protected IExamAnalysisService.ExamSummary call() {
                return service.computeExamSummary(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            IExamAnalysisService.ExamSummary s = task.getValue();
            summaryMean.setText(String.format("%.1f", s.overallMean()));
            summaryStudents.setText(String.valueOf(s.totalStudents()));
            summarySubjects.setText(String.valueOf(s.totalSubjects()));
            summaryPassRate.setText(String.format("%.1f%%", s.passRate()));
            summaryHighest.setText(String.format("%.1f", s.highestScore()));
            summaryLowest.setText(String.format("%.1f", s.lowestScore()));
            summaryBestSubj.setText(s.bestSubject());
            summaryWorstSubj.setText(s.worstSubject());
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

    private void loadWeakAreas(long examId) {
        Task<List<IExamAnalysisService.WeakArea>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.WeakArea> call() {
                return service.computeWeakAreas(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.WeakArea> weakAreas = task.getValue();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (IExamAnalysisService.WeakArea wa : weakAreas)
                series.getData().add(new XYChart.Data<>(wa.subjectName(), wa.meanScore()));
            weakAreasChart.getData().clear();
            weakAreasChart.getData().add(series);
            styleBarSeries(series);
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

    private void loadTrend() {
        Task<List<IExamAnalysisService.ClassTrend>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.ClassTrend> call() {
                return service.computeClassTrends();
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.ClassTrend> trends = task.getValue();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            for (int i = 0; i < trends.size(); i++)
                series.getData().add(new XYChart.Data<>(i + 1, trends.get(i).meanScore()));
            classTrendChart.getData().clear();
            classTrendChart.getData().add(series);
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

    private HBox buildSummaryCards() {
        HBox cards = new HBox(12);
        cards.setPadding(new Insets(5, 0, 5, 0));
        Node[] cardNodes = {
            summaryCard("Overall Mean", summaryMean, AppTheme.RED),
            summaryCard("Students", summaryStudents, AppTheme.GREEN),
            summaryCard("Subjects", summarySubjects, AppTheme.WHITE_BG),
            summaryCard("Pass Rate", summaryPassRate, AppTheme.RED),
            summaryCard("Highest", summaryHighest, AppTheme.GREEN),
            summaryCard("Lowest", summaryLowest, AppTheme.WHITE_BG),
            summaryCard("Best Subject", summaryBestSubj, AppTheme.RED),
            summaryCard("Needs Focus", summaryWorstSubj, AppTheme.GREEN),
        };
        cards.getChildren().addAll(cardNodes);
        return cards;
    }

    private VBox summaryCard(String title, Label valueLabel, String bgColor) {
        VBox card = new VBox(3);
        card.setPrefSize(130, 65);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        boolean isWhite = AppTheme.WHITE_BG.equals(bgColor);
        String textColor = isWhite ? "#333333" : "white";
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6, 0, 0, 2);");
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("System", 10));
        titleLbl.setTextFill(isWhite ? javafx.scene.paint.Color.gray(0.4) : javafx.scene.paint.Color.web("white", 0.8));
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        valueLabel.setTextFill(javafx.scene.paint.Color.web(textColor));
        card.getChildren().addAll(valueLabel, titleLbl);
        return card;
    }

    private BarChart<String, Number> buildWeakAreasChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Subject");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Subject Performance (Ascending)");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        return chart;
    }

    private LineChart<Number, Number> buildClassTrendChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Exam #");
        xAxis.setTickUnit(1);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Mean Score");
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Overall Mean Score per Exam");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        return chart;
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
}
