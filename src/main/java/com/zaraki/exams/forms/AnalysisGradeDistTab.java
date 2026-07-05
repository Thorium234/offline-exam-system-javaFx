package com.zaraki.exams.forms;

import com.zaraki.exams.service.IExamAnalysisService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

public class AnalysisGradeDistTab {

    private static final String[] CHART_COLORS = {AppTheme.RED, AppTheme.GREEN, AppTheme.WHITE_BG};

    private final IExamAnalysisService service;
    private final Stage stage;
    private final PieChart pieChart;
    private final TableView<GradeDistRow> table = new TableView<>();

    public AnalysisGradeDistTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
        this.pieChart = buildPieChart();
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        pieChart.setPrefHeight(300);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        content.getChildren().addAll(pieChart, table);
        return content;
    }

    public void load(long examId) {
        Task<List<IExamAnalysisService.GradeDistribution>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.GradeDistribution> call() {
                return service.computeGradeDistribution(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.GradeDistribution> gradeData = task.getValue();

            Map<String, Integer> overallGrades = new LinkedHashMap<>();
            for (var gd : gradeData) {
                for (var entry : gd.gradeCounts().entrySet())
                    overallGrades.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (var entry : overallGrades.entrySet())
                pieData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
            pieChart.setData(pieData);
            stylePieData();

            table.getColumns().clear();
            Set<String> allGrades = new LinkedHashSet<>();
            for (var gd : gradeData) allGrades.addAll(gd.gradeCounts().keySet());
            List<String> gradeOrder = new ArrayList<>(allGrades);

            TableColumn<GradeDistRow, String> cSubject = new TableColumn<>("Subject");
            cSubject.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
            cSubject.setPrefWidth(200);
            table.getColumns().add(cSubject);

            for (String g : gradeOrder) {
                TableColumn<GradeDistRow, Number> col = new TableColumn<>(g);
                col.setPrefWidth(50);
                final String grade = g;
                col.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().gradeCounts.getOrDefault(grade, 0)));
                table.getColumns().add(col);
            }
            ObservableList<GradeDistRow> rows = FXCollections.observableArrayList();
            for (var gd : gradeData) rows.add(new GradeDistRow(gd.subjectName(), gd.gradeCounts()));
            table.setItems(rows);
        });
        task.setOnFailed(ev -> {});
        new Thread(task).start();
    }

    private PieChart buildPieChart() {
        PieChart chart = new PieChart();
        chart.setTitle("Overall Grade Distribution");
        chart.setAnimated(false);
        chart.setLabelsVisible(true);
        return chart;
    }

    private void stylePieData() {
        int i = 0;
        for (var data : pieChart.getData()) {
            String color = CHART_COLORS[i % CHART_COLORS.length];
            data.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
            });
            i++;
        }
    }

    public static class GradeDistRow {
        private final String subjectName;
        public final Map<String, Integer> gradeCounts;
        public GradeDistRow(String subjectName, Map<String, Integer> gradeCounts) {
            this.subjectName = subjectName;
            this.gradeCounts = gradeCounts;
        }
        public String getSubjectName() { return subjectName; }
    }
}
