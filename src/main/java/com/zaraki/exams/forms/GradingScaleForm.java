package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.repository.IGradingScaleRepository;
import com.zaraki.exams.repository.GradingScaleRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;


public class GradingScaleForm {

    private final SettingsManager settings;
    private final IGradingScaleRepository gradingRepo;
    private final TableView<ScaleRow> table;
    private final ObservableList<ScaleRow> data;
    private final ComboBox<String> subjectBox;

    public GradingScaleForm(SettingsManager settings) {
        this.settings = settings;
        this.gradingRepo = new GradingScaleRepositoryImpl();
        this.table = new TableView<>();
        this.data = FXCollections.observableArrayList();
        this.subjectBox = new ComboBox<>();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Grading Scales");

        CurriculumSystem curr = settings.getCurriculum();
        Label info = new Label("Active: " + curr.getDisplayName()
            + " | Leave Subject blank for global scale.");

        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(10);

        subjectBox.getItems().add("-- Global --");
        subjectBox.setValue("-- Global --");
        try {
            var subjects = gradingRepo.findAllSubjectsForCombo();
            for (var s : subjects)
                subjectBox.getItems().add(s.get("id") + ":" + s.get("subject_name"));
        } catch (Exception e) { UIUtils.showError(e.getMessage()); }

        TextField minField = new TextField(); minField.setPromptText("Min");
        TextField maxField = new TextField(); maxField.setPromptText("Max");
        TextField gradeField = new TextField(); gradeField.setPromptText("Grade");
        TextField pointsField = new TextField(); pointsField.setPromptText("Points");
        TextField remarksField = new TextField(); remarksField.setPromptText("Remarks");

        form.add(new Label("Subject:"), 0, 0); form.add(subjectBox, 1, 0);
        form.add(new Label("Min:"), 2, 0); form.add(minField, 3, 0);
        form.add(new Label("Max:"), 0, 1); form.add(maxField, 1, 1);
        form.add(new Label("Grade:"), 2, 1); form.add(gradeField, 3, 1);
        form.add(new Label("Points:"), 0, 2); form.add(pointsField, 1, 2);
        form.add(new Label("Remarks:"), 2, 2); form.add(remarksField, 3, 2);

        HBox btnRow = new HBox(10);
        Button addBtn = new Button("Add Scale");
        Button autoBtn = new Button("Auto-Generate " + curr.getDisplayName() + " Scales");
        autoBtn.getStyleClass().addAll("button", "button-success");
        btnRow.getChildren().addAll(addBtn, autoBtn);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ScaleRow, String> cSubj = new TableColumn<>("Subject");
        cSubj.setCellValueFactory(new PropertyValueFactory<>("subject")); cSubj.setPrefWidth(140);
        TableColumn<ScaleRow, Double> cMin = new TableColumn<>("Min");
        cMin.setCellValueFactory(new PropertyValueFactory<>("minimum")); cMin.setPrefWidth(70);
        TableColumn<ScaleRow, Double> cMax = new TableColumn<>("Max");
        cMax.setCellValueFactory(new PropertyValueFactory<>("maximum")); cMax.setPrefWidth(70);
        TableColumn<ScaleRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("grade")); cGrade.setPrefWidth(80);
        TableColumn<ScaleRow, Integer> cPoints = new TableColumn<>("Points");
        cPoints.setCellValueFactory(new PropertyValueFactory<>("points")); cPoints.setPrefWidth(70);
        TableColumn<ScaleRow, String> cRemarks = new TableColumn<>("Remarks");
        cRemarks.setCellValueFactory(new PropertyValueFactory<>("remarks")); cRemarks.setPrefWidth(140);

        TableColumn<ScaleRow, Void> colEdit = new TableColumn<>("Edit");
        colEdit.setPrefWidth(60);
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ScaleRow row = getTableRow().getItem();
                    btn.setOnAction(e -> editScale(row));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<ScaleRow, Void> colDel = new TableColumn<>("");
        colDel.setPrefWidth(60);
        colDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Del");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ScaleRow row = getTableRow().getItem();
                    btn.setOnAction(e -> deleteScale(row));
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(cSubj, cMin, cMax, cGrade, cPoints, cRemarks, colEdit, colDel);
        table.setPrefHeight(350);

        load();
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try {
                String minText = minField.getText().trim();
                String maxText = maxField.getText().trim();
                String grade = gradeField.getText().trim();
                String ptsText = pointsField.getText().trim();
                if (minText.isEmpty() || maxText.isEmpty() || grade.isEmpty() || ptsText.isEmpty()) {
                    UIUtils.showError("Min, Max, Grade, and Points are required.");
                    return;
                }
                double min, max;
                int pts;
                try {
                    min = Double.parseDouble(minText);
                    max = Double.parseDouble(maxText);
                    pts = Integer.parseInt(ptsText);
                    if (min < 0 || max < 0 || pts < 0) throw new NumberFormatException();
                    if (min >= max) { UIUtils.showError("Min must be less than Max."); return; }
                } catch (NumberFormatException ex) {
                    UIUtils.showError("Min and Max must be valid positive numbers; Points must be a valid positive integer.");
                    return;
                }
                String subj = subjectBox.getValue();
                Long subjectId = (subj == null || subj.equals("-- Global --")) ? null
                    : Long.parseLong(subj.split(":")[0]);
                gradingRepo.insert(subjectId, min, max, grade, pts, remarksField.getText().trim());
                load();
                minField.clear(); maxField.clear(); gradeField.clear(); pointsField.clear(); remarksField.clear();
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        autoBtn.setOnAction(e -> autoGenerate());

        view.getChildren().addAll(header, info, form, btnRow, table);
        return view;
    }

    private void autoGenerate() {
        CurriculumSystem curr = settings.getCurriculum();
        try {
            gradingRepo.deleteGlobal();
            gradingRepo.insertBatchGlobal(curr);
            load();
            UIUtils.showInfo("Auto-generated " + curr.getPresetGrades().size() + " global grading scales for " + curr.getDisplayName());
        } catch (Exception e) {
            UIUtils.showError("Error: " + e.getMessage());
        }
    }

    public void refresh() {
        load();
    }

    private void editScale(ScaleRow row) {
        Dialog<ScaleRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Grading Scale");
        dialog.setHeaderText("Edit " + row.getGrade() + " (" + row.getSubject() + ")");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField minField = new TextField(String.valueOf(row.getMinimum()));
        TextField maxField = new TextField(String.valueOf(row.getMaximum()));
        TextField gradeField = new TextField(row.getGrade());
        TextField pointsField = new TextField(String.valueOf(row.getPoints()));
        TextField remarksField = new TextField(row.getRemarks());

        grid.add(new Label("Min:"), 0, 0); grid.add(minField, 1, 0);
        grid.add(new Label("Max:"), 0, 1); grid.add(maxField, 1, 1);
        grid.add(new Label("Grade:"), 0, 2); grid.add(gradeField, 1, 2);
        grid.add(new Label("Points:"), 0, 3); grid.add(pointsField, 1, 3);
        grid.add(new Label("Remarks:"), 0, 4); grid.add(remarksField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogBtn -> {
            if (dialogBtn == saveType) {
                try {
                    double min = Double.parseDouble(minField.getText().trim());
                    double max = Double.parseDouble(maxField.getText().trim());
                    int pts = Integer.parseInt(pointsField.getText().trim());
                    if (min >= max) { UIUtils.showError("Min must be less than Max."); return null; }
                    return new ScaleRow(row.getId(), row.getSubject(), min, max,
                        gradeField.getText().trim(), pts, remarksField.getText().trim());
                } catch (NumberFormatException ex) {
                    UIUtils.showError("Invalid numeric values.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                gradingRepo.update(result.getId(), null, result.getMinimum(), result.getMaximum(),
                    result.getGrade(), result.getPoints(), result.getRemarks());
                load();
                UIUtils.showInfo("Scale updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteScale(ScaleRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete grading scale " + row.getGrade() + " (" + row.getMinimum() + "-" + row.getMaximum() + ")?",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            gradingRepo.deleteById(row.getId());
            load();
            UIUtils.showInfo("Scale deleted.");
        } catch (Exception ex) { UIUtils.showError("Cannot delete: " + ex.getMessage()); }
    }

    private void load() {
        data.clear();
        try {
            var scales = gradingRepo.findAllWithSubject();
            for (var s : scales)
                data.add(new ScaleRow(
                    (Long) s.get("id"),
                    (String) s.get("subject_name"),
                    (Double) s.get("minimum_mark"),
                    (Double) s.get("maximum_mark"),
                    (String) s.get("grade"),
                    (Integer) s.get("points"),
                    (String) s.get("remarks")));
            if (data.isEmpty()) {
                table.setPlaceholder(new EmptyStatePlaceholder("No grading scales defined. Auto-generate or add one above.", "\uD83C\uDFAF").getView());
            }
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    public static class ScaleRow {
        private final Long id; private final String subject; private final Double minimum, maximum;
        private final String grade; private final Integer points; private final String remarks;
        public ScaleRow(Long id, String subject, Double min, Double max, String grade, Integer points, String remarks) {
            this.id = id; this.subject = subject; this.minimum = min; this.maximum = max;
            this.grade = grade; this.points = points; this.remarks = remarks;
        }
        public Long getId() { return id; }
        public String getSubject() { return subject; }
        public Double getMinimum() { return minimum; }
        public Double getMaximum() { return maximum; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
        public String getRemarks() { return remarks; }
    }
}
