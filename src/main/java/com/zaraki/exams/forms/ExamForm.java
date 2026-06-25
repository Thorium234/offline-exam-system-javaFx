package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.ExamRepository;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ExamForm {

    private final ExamRepository examRepo;

    public ExamForm(DatabaseEngine db) {
        this.examRepo = new ExamRepository();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Exams");

        HBox form = new HBox(10);
        TextField yearField = new TextField(); yearField.setPromptText("Year (e.g. 2026)");
        ComboBox<String> termBox = new ComboBox<>(FXCollections.observableArrayList("Term 1", "Term 2", "Term 3"));
        termBox.setPromptText("Term");
        TextField seriesField = new TextField(); seriesField.setPromptText("Series (e.g. End-Term)");
        Button addBtn = new Button("Create");
        form.getChildren().addAll(yearField, termBox, seriesField, addBtn);

        TableView<ExamRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
            UIUtils.<ExamRow>col("ID", "id", 60),
            UIUtils.<ExamRow>col("Year", "year", 120),
            UIUtils.<ExamRow>col("Term", "term", 120),
            UIUtils.<ExamRow>col("Series", "series", 180),
            UIUtils.<ExamRow>col("Max Marks", "maxMarks", 90)
        );
        table.setPrefHeight(400);

        ObservableList<ExamRow> data = FXCollections.observableArrayList();
        load(data);
        table.setItems(data);

        addBtn.setOnAction(e -> {
            String year = yearField.getText().trim();
            String term = termBox.getValue();
            String series = seriesField.getText().trim();
            if (year.isEmpty() || term == null || series.isEmpty()) {
                UIUtils.showError("Year, Term, and Series are required.");
                return;
            }
            if (!year.matches("\\d{4}")) {
                UIUtils.showError("Year must be a 4-digit number (e.g. 2026).");
                return;
            }
            try {
                examRepo.insert(year, term, series);
                load(data);
                yearField.clear(); termBox.setValue(null); seriesField.clear();
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load(ObservableList<ExamRow> data) {
        data.clear();
        try {
            var exams = examRepo.findAll();
            for (var e : exams)
                data.add(new ExamRow(
                    (Long) e.get("id"),
                    (String) e.get("academic_year"),
                    (String) e.get("term"),
                    (String) e.get("exam_series"),
                    (Integer) e.get("max_marks")));
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    public static class ExamRow {
        private final Long id; private final String year, term, series;
        private final Integer maxMarks;
        public ExamRow(Long id, String year, String term, String series, Integer maxMarks) {
            this.id = id; this.year = year; this.term = term; this.series = series;
            this.maxMarks = maxMarks;
        }
        public Long getId() { return id; }
        public String getYear() { return year; }
        public String getTerm() { return term; }
        public String getSeries() { return series; }
        public Integer getMaxMarks() { return maxMarks; }
    }
}
