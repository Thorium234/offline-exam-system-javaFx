package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class GradingScaleForm {

    private final DatabaseEngine db;
    private final SettingsManager settings;
    private final TableView<ScaleRow> table;
    private final ObservableList<ScaleRow> data;
    private final ComboBox<String> subjectBox;

    public GradingScaleForm(DatabaseEngine db, SettingsManager settings) {
        this.db = db;
        this.settings = settings;
        this.table = new TableView<>();
        this.data = FXCollections.observableArrayList();
        this.subjectBox = new ComboBox<>();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Grading Scales");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        CurriculumSystem curr = settings.getCurriculum();
        Label info = new Label("Active: " + curr.getDisplayName()
            + " | Leave Subject blank for global scale.");
        info.setFont(Font.font("System", 14));
        info.setTextFill(Color.gray(0.4));

        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(10);

        subjectBox.getItems().add("-- Global --");
        subjectBox.setValue("-- Global --");
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_name FROM subjects ORDER BY subject_name")) {
            while (rs.next())
                subjectBox.getItems().add(rs.getLong("id") + ":" + rs.getString("subject_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }

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
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?,?,?,?,?,?)")) {
                String subj = subjectBox.getValue();
                if (subj == null || subj.equals("-- Global --")) ps.setNull(1, Types.INTEGER);
                else ps.setLong(1, Long.parseLong(subj.split(":")[0]));
                ps.setDouble(2, Double.parseDouble(minField.getText()));
                ps.setDouble(3, Double.parseDouble(maxField.getText()));
                ps.setString(4, gradeField.getText());
                ps.setInt(5, Integer.parseInt(pointsField.getText()));
                ps.setString(6, remarksField.getText());
                ps.executeUpdate();
                load();
                minField.clear(); maxField.clear(); gradeField.clear(); pointsField.clear(); remarksField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        autoBtn.setOnAction(e -> autoGenerate());

        view.getChildren().addAll(header, info, form, btnRow, table);
        return view;
    }

    private void autoGenerate() {
        CurriculumSystem curr = settings.getCurriculum();
        try (Connection conn = db.getConnection();
             PreparedStatement del = conn.prepareStatement("DELETE FROM grading_scales WHERE subject_id IS NULL");
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL,?,?,?,?,?)")) {
            del.executeUpdate();
            conn.setAutoCommit(false);
            try {
                for (CurriculumSystem.PresetGrade pg : curr.getPresetGrades()) {
                    ins.setDouble(1, pg.min());
                    ins.setDouble(2, pg.max());
                    ins.setString(3, pg.grade());
                    ins.setInt(4, pg.points());
                    ins.setString(5, pg.remarks());
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            load();
            showAlert("Auto-generated " + curr.getPresetGrades().size() + " global grading scales for " + curr.getDisplayName());
        } catch (Exception e) {
            showAlert("Error: " + e.getMessage());
        }
    }

    public void refresh() {
        load();
    }

    private void load() {
        data.clear();
        String sql = """
            SELECT gs.id, gs.minimum_mark, gs.maximum_mark, gs.grade, gs.points, gs.remarks,
                   COALESCE(sub.subject_name, '** Global **') AS subject_name
            FROM grading_scales gs
            LEFT JOIN subjects sub ON sub.id = gs.subject_id
            ORDER BY gs.subject_id IS NULL DESC, gs.points DESC
            """;
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                data.add(new ScaleRow(rs.getString("subject_name"), rs.getDouble("minimum_mark"),
                    rs.getDouble("maximum_mark"), rs.getString("grade"),
                    rs.getInt("points"), rs.getString("remarks")));
        } catch (SQLException e) {
            showAlert(e.getMessage());
        }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
            a.showAndWait();
        });
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
