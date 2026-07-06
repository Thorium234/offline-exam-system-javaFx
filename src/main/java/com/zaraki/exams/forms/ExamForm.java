package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.IExamRepository;
import com.zaraki.exams.repository.ExamRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ExamForm {

    private final IExamRepository examRepo;
    private final TableView<ExamRow> table;
    private final ObservableList<ExamRow> data;

    public ExamForm(DatabaseEngine db) {
        this.examRepo = new ExamRepositoryImpl();
        this.table = new TableView<>();
        this.data = FXCollections.observableArrayList();
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

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
            UIUtils.<ExamRow>col("ID", "id", 60),
            UIUtils.<ExamRow>col("Year", "year", 120),
            UIUtils.<ExamRow>col("Term", "term", 120),
            UIUtils.<ExamRow>col("Series", "series", 180),
            UIUtils.<ExamRow>col("Max Marks", "maxMarks", 90)
        );

        TableColumn<ExamRow, Void> colEdit = new TableColumn<>("Edit");
        colEdit.setPrefWidth(60);
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ExamRow row = getTableRow().getItem();
                    btn.setOnAction(e -> editExam(row));
                    setGraphic(btn);
                }
            }
        });
        table.getColumns().add(colEdit);

        TableColumn<ExamRow, Void> colDel = new TableColumn<>("");
        colDel.setPrefWidth(60);
        colDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Delete");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ExamRow row = getTableRow().getItem();
                    btn.setOnAction(e -> deleteExam(row));
                    setGraphic(btn);
                }
            }
        });
        table.getColumns().add(colDel);

        table.setPrefHeight(400);

        load();
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
                load();
                yearField.clear(); termBox.setValue(null); seriesField.clear();
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load() {
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

    private void editExam(ExamRow row) {
        Dialog<ExamRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Exam");
        dialog.setHeaderText("Edit Exam #" + row.getId());

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField yearField = new TextField(row.getYear());
        ComboBox<String> termBox = new ComboBox<>(FXCollections.observableArrayList("Term 1", "Term 2", "Term 3"));
        termBox.setValue(row.getTerm());
        TextField seriesField = new TextField(row.getSeries());

        grid.add(new Label("Year:"), 0, 0); grid.add(yearField, 1, 0);
        grid.add(new Label("Term:"), 0, 1); grid.add(termBox, 1, 1);
        grid.add(new Label("Series:"), 0, 2); grid.add(seriesField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogBtn -> {
            if (dialogBtn == saveType) {
                String year = yearField.getText().trim();
                String term = termBox.getValue();
                String series = seriesField.getText().trim();
                if (year.isEmpty() || term == null || series.isEmpty()) {
                    UIUtils.showError("All fields are required.");
                    return null;
                }
                return new ExamRow(row.getId(), year, term, series, row.getMaxMarks());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                examRepo.update(result.getId(), result.getYear(), result.getTerm(), result.getSeries());
                load();
                UIUtils.showInfo("Exam updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteExam(ExamRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete exam '" + row.getYear() + " " + row.getTerm() + " " + row.getSeries() + "'?\n"
            + "This will also delete ALL marks for this exam. This cannot be undone!",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            examRepo.delete(row.getId());
            load();
            UIUtils.showInfo("Exam deleted.");
        } catch (Exception ex) { UIUtils.showError("Cannot delete: " + ex.getMessage()); }
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
