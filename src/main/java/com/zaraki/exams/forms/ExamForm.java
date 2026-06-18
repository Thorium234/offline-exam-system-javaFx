package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class ExamForm {

    private final DatabaseEngine db;

    public ExamForm(DatabaseEngine db) {
        this.db = db;
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Exams");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField yearField = new TextField(); yearField.setPromptText("Year (e.g. 2026)");
        ComboBox<String> termBox = new ComboBox<>(FXCollections.observableArrayList("Term 1", "Term 2", "Term 3"));
        termBox.setPromptText("Term");
        TextField seriesField = new TextField(); seriesField.setPromptText("Series (e.g. End-Term)");
        Button addBtn = new Button("Create");
        form.getChildren().addAll(yearField, termBox, seriesField, addBtn);

        TableView<ExamRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ExamRow, Long> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id")); cId.setPrefWidth(60);
        TableColumn<ExamRow, String> cYear = new TableColumn<>("Year");
        cYear.setCellValueFactory(new PropertyValueFactory<>("year")); cYear.setPrefWidth(120);
        TableColumn<ExamRow, String> cTerm = new TableColumn<>("Term");
        cTerm.setCellValueFactory(new PropertyValueFactory<>("term")); cTerm.setPrefWidth(120);
        TableColumn<ExamRow, String> cSeries = new TableColumn<>("Series");
        cSeries.setCellValueFactory(new PropertyValueFactory<>("series")); cSeries.setPrefWidth(200);
        table.getColumns().addAll(cId, cYear, cTerm, cSeries);
        table.setPrefHeight(400);

        ObservableList<ExamRow> data = FXCollections.observableArrayList();
        load(data);
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)")) {
                ps.setString(1, yearField.getText());
                ps.setString(2, termBox.getValue());
                ps.setString(3, seriesField.getText());
                ps.executeUpdate();
                load(data);
                yearField.clear(); termBox.setValue(null); seriesField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load(ObservableList<ExamRow> data) {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams")) {
            while (rs.next())
                data.add(new ExamRow(rs.getLong("id"), rs.getString("academic_year"),
                    rs.getString("term"), rs.getString("exam_series")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public static class ExamRow {
        private final Long id; private final String year, term, series;
        public ExamRow(Long id, String year, String term, String series) {
            this.id = id; this.year = year; this.term = term; this.series = series;
        }
        public Long getId() { return id; }
        public String getYear() { return year; }
        public String getTerm() { return term; }
        public String getSeries() { return series; }
    }
}
