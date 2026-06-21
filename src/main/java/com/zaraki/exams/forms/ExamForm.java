package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;

public class ExamForm {

    private final DatabaseEngine db;

    public ExamForm(DatabaseEngine db) {
        this.db = db;
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
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)")) {
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
                ps.setString(1, year);
                ps.setString(2, term);
                ps.setString(3, series);
                ps.executeUpdate();
                load(data);
                yearField.clear(); termBox.setValue(null); seriesField.clear();
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load(ObservableList<ExamRow> data) {
        data.clear();
        String sql = """
            SELECT e.id, e.academic_year, e.term, e.exam_series,
                   COALESCE((SELECT SUM(COALESCE(es.out_of, 100)) FROM exam_subjects es WHERE es.exam_id = e.id), 0) AS max_marks
            FROM exams e ORDER BY e.id
            """;
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                data.add(new ExamRow(rs.getLong("id"), rs.getString("academic_year"),
                    rs.getString("term"), rs.getString("exam_series"), rs.getInt("max_marks")));
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
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
