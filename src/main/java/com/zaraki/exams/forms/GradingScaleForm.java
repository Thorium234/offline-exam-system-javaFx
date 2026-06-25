package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.repository.GradingScaleRepository;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;


public class GradingScaleForm {

    private final SettingsManager settings;
    private final GradingScaleRepository gradingRepo;
    private final TableView<ScaleRow> table;
    private final ObservableList<ScaleRow> data;
    private final ComboBox<String> subjectBox;

    public GradingScaleForm(SettingsManager settings) {
        this.settings = settings;
        this.gradingRepo = new GradingScaleRepository();
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
        autoBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
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
        cRemarks.setCellValueFactory(new PropertyValueFactory<>("remarks")); cRemarks.setPrefWidth(180);
        table.getColumns().addAll(cSubj, cMin, cMax, cGrade, cPoints, cRemarks);
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

    private void load() {
        data.clear();
        try {
            var scales = gradingRepo.findAllWithSubject();
            for (var s : scales)
                data.add(new ScaleRow(
                    (String) s.get("subject_name"),
                    (Double) s.get("minimum_mark"),
                    (Double) s.get("maximum_mark"),
                    (String) s.get("grade"),
                    (Integer) s.get("points"),
                    (String) s.get("remarks")));
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    public static class ScaleRow {
        private final String subject; private final Double minimum, maximum;
        private final String grade; private final Integer points; private final String remarks;
        public ScaleRow(String subject, Double min, Double max, String grade, Integer points, String remarks) {
            this.subject = subject; this.minimum = min; this.maximum = max;
            this.grade = grade; this.points = points; this.remarks = remarks;
        }
        public String getSubject() { return subject; }
        public Double getMinimum() { return minimum; }
        public Double getMaximum() { return maximum; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
        public String getRemarks() { return remarks; }
    }
}
