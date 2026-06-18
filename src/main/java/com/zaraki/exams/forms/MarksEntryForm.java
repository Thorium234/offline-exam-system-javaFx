package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class MarksEntryForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;

    public MarksEntryForm(DatabaseEngine db) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Marks Entry");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Select an exam, then enter scores. Grade and Points auto-calculate from grading scales.");
        info.setFont(Font.font("System", 14));
        info.setTextFill(Color.gray(0.4));

        HBox controls = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(250);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }

        TextField scoreField = new TextField(); scoreField.setPromptText("Score"); scoreField.setPrefWidth(100);
        Button saveBtn = new Button("Save (Auto-Grade)");

        controls.getChildren().addAll(examBox, scoreField, saveBtn);

        TableView<MarkRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<MarkRow, String> cSName = new TableColumn<>("Student");
        cSName.setCellValueFactory(new PropertyValueFactory<>("studentName")); cSName.setPrefWidth(180);
        TableColumn<MarkRow, String> cSubName = new TableColumn<>("Subject");
        cSubName.setCellValueFactory(new PropertyValueFactory<>("subjectName")); cSubName.setPrefWidth(180);
        TableColumn<MarkRow, Double> cScore = new TableColumn<>("Score");
        cScore.setCellValueFactory(new PropertyValueFactory<>("score")); cScore.setPrefWidth(80);
        TableColumn<MarkRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("grade")); cGrade.setPrefWidth(70);
        TableColumn<MarkRow, Integer> cPoints = new TableColumn<>("Points");
        cPoints.setCellValueFactory(new PropertyValueFactory<>("points")); cPoints.setPrefWidth(70);
        table.getColumns().addAll(cSName, cSubName, cScore, cGrade, cPoints);
        table.setPrefHeight(400);

        view.getChildren().addAll(header, info, controls, table);

        saveBtn.setOnAction(e -> {
            MarkRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String examStr = examBox.getValue();
            if (examStr == null) return;
            long examId = Long.parseLong(examStr.split(" - ")[0]);
            double score = Double.parseDouble(scoreField.getText());

            long studentId = resolveId("students", "full_name", selected.studentName);
            long subjectId = resolveId("subjects", "subject_name", selected.subjectName);
            if (studentId < 0 || subjectId < 0) return;

            String gradeResult = analysisService.determineGradeAndPoints(score, subjectId);
            String[] parts = gradeResult.split("\\|");
            String grade = parts[0];
            int points = Integer.parseInt(parts[1]);

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score, grade_achieved, points_achieved) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, examId);
                ps.setLong(2, studentId);
                ps.setLong(3, subjectId);
                ps.setDouble(4, score);
                ps.setString(5, grade);
                ps.setInt(6, points);
                ps.executeUpdate();

                selected.score = score;
                selected.grade = grade;
                selected.points = points;
                table.refresh();
                scoreField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            ObservableList<MarkRow> marks = FXCollections.observableArrayList();
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT s.full_name, sub.subject_name, m.score, m.grade_achieved, m.points_achieved " +
                     "FROM students s CROSS JOIN subjects sub " +
                     "LEFT JOIN marks m ON m.student_id = s.id AND m.subject_id = sub.id AND m.exam_id = " + examId +
                     " ORDER BY s.full_name, sub.subject_name")) {
                while (rs.next())
                    marks.add(new MarkRow(rs.getString("full_name"), rs.getString("subject_name"),
                        rs.getObject("score") != null ? rs.getDouble("score") : null,
                        rs.getString("grade_achieved"),
                        rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null));
            } catch (SQLException ex) { showAlert(ex.getMessage()); }
            table.setItems(marks);
        });

        return view;
    }

    private long resolveId(String table, String nameCol, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM " + table + " WHERE " + nameCol + " = ?")) {
            ps.setString(1, value);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        } catch (SQLException e) { showAlert(e.getMessage()); }
        return -1;
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public static class MarkRow {
        private final String studentName, subjectName;
        private Double score; private String grade; private Integer points;
        public MarkRow(String studentName, String subjectName, Double score, String grade, Integer points) {
            this.studentName = studentName; this.subjectName = subjectName;
            this.score = score; this.grade = grade; this.points = points;
        }
        public String getStudentName() { return studentName; }
        public String getSubjectName() { return subjectName; }
        public Double getScore() { return score; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
    }
}
