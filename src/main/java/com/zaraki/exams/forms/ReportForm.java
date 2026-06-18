package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final ReportCardGenerator reportGenerator;
    private final Stage stage;

    private VBox previewBox;
    private Label statusLabel;
    private long selectedExamId;
    private long selectedStudentId;

    public ReportForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.reportGenerator = new ReportCardGenerator();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Reports");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox examRow = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        examRow.getChildren().addAll(new Label("Exam:"), examBox);

        HBox studentRow = new HBox(10);
        ComboBox<String> studentBox = new ComboBox<>();
        studentBox.setPromptText("Select Student");
        studentBox.setPrefWidth(300);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name FROM students ORDER BY full_name")) {
            while (rs.next())
                studentBox.getItems().add(rs.getLong("id") + " - " + rs.getString("admission_number") + " - " + rs.getString("full_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        studentRow.getChildren().addAll(new Label("Student:"), studentBox);

        HBox btnRow = new HBox(10);
        Button previewBtn = new Button("Preview Report");
        Button genPdfBtn = new Button("Generate PDF");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);
        btnRow.getChildren().addAll(previewBtn, genPdfBtn, spinner);

        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.gray(0.4));

        previewBox = new VBox(10);
        previewBox.setPadding(new Insets(15));
        previewBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        previewBox.setVisible(false);

        ScrollPane previewScroll = new ScrollPane(previewBox);
        previewScroll.setFitToWidth(true);
        previewScroll.setPrefHeight(400);
        previewScroll.setStyle("-fx-background-color: transparent;");
        previewScroll.setVisible(false);

        view.getChildren().addAll(header, examRow, studentRow, btnRow, statusLabel, previewScroll);

        previewBtn.setOnAction(e -> {
            if (examBox.getValue() == null || studentBox.getValue() == null) {
                showAlert("Select both an exam and a student.");
                return;
            }
            selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            selectedStudentId = Long.parseLong(studentBox.getValue().split(" - ")[0]);
            statusLabel.setText("Generating preview...");
            spinner.setVisible(true);

            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    return null; // data fetch happens on FX thread via loadPreviewData
                }
            };
            task.setOnSucceeded(ev -> {
                loadPreviewData(selectedExamId, selectedStudentId);
                spinner.setVisible(false);
                previewScroll.setVisible(true);
                statusLabel.setText("Preview ready.");
            });
            new Thread(task).start();
        });

        genPdfBtn.setOnAction(e -> {
            if (examBox.getValue() == null || studentBox.getValue() == null) {
                showAlert("Select both an exam and a student.");
                return;
            }
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            long studentId = Long.parseLong(studentBox.getValue().split(" - ")[0]);

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Report Card");
            fc.setInitialFileName("report_card.pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            spinner.setVisible(true);
            statusLabel.setText("Generating PDF...");

            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    reportGenerator.generateStudentReport(examId, studentId, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Saved to: " + file.getAbsolutePath());
                showInfo("Report card saved successfully.");
            });
            task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); showAlert(task.getException().getMessage()); });
            new Thread(task).start();
        });

        return view;
    }

    private void loadPreviewData(long examId, long studentId) {
        previewBox.getChildren().clear();

        // Header
        Label title = new Label("THORIUM EXAM ANALYSIS SYSTEM");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(PRIMARY));
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        Label schoolLabel = new Label("Official Report Form - Kenya Secondary School");
        schoolLabel.setFont(Font.font("System", 12));
        schoolLabel.setAlignment(Pos.CENTER);
        schoolLabel.setMaxWidth(Double.MAX_VALUE);

        previewBox.getChildren().addAll(title, schoolLabel);
        previewBox.getChildren().add(new Separator());

        // Exam & Student info
        String examSql = "SELECT academic_year, term, exam_series FROM exams WHERE id = ?";
        String studentSql = "SELECT admission_number, full_name, form, stream FROM students WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement eps = conn.prepareStatement(examSql);
             PreparedStatement sps = conn.prepareStatement(studentSql)) {

            eps.setLong(1, examId);
            ResultSet er = eps.executeQuery();
            if (er.next()) {
                previewBox.getChildren().add(new Label("Exam: " + er.getString("academic_year") + " - " + er.getString("term") + " - " + er.getString("exam_series")));
            }

            sps.setLong(1, studentId);
            ResultSet sr = sps.executeQuery();
            if (sr.next()) {
                previewBox.getChildren().add(new Label("Student: " + sr.getString("admission_number") + " - " + sr.getString("full_name")));
                previewBox.getChildren().add(new Label("Class: Form " + sr.getInt("form") + " - " + sr.getString("stream")));
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(new Separator());

        // Subject table header
        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(5);
        grid.setPadding(new Insets(5, 0, 5, 0));
        grid.setStyle("-fx-background-color: " + PRIMARY + "; -fx-background-radius: 4; -fx-padding: 8;");

        String[] cols = {"Subject", "Score", "Grade", "Points", "Position", "Remarks"};
        for (int i = 0; i < cols.length; i++) {
            Label l = new Label(cols[i]);
            l.setFont(Font.font("System", FontWeight.BOLD, 11));
            l.setTextFill(Color.WHITE);
            grid.add(l, i, 0);
        }
        previewBox.getChildren().add(grid);

        // Subject rows
        String marksSql = """
            SELECT sub.subject_name, m.score, m.grade_achieved, m.points_achieved,
                (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                 WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id AND m2.score > m.score) AS pos,
                (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2
                 WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id) AS total_students
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ? AND m.student_id = ?
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(marksSql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            int row = 0;
            while (rs.next()) {
                GridPane rowGrid = new GridPane();
                rowGrid.setHgap(15);
                rowGrid.setPadding(new Insets(4, 8, 4, 8));
                if (row % 2 == 0) rowGrid.setStyle("-fx-background-color: #f9f9f9;");

                rowGrid.add(new Label(rs.getString("subject_name")), 0, 0);
                rowGrid.add(new Label(rs.getObject("score") != null ? String.valueOf(rs.getDouble("score")) : "-"), 1, 0);
                rowGrid.add(new Label(rs.getString("grade_achieved") != null ? rs.getString("grade_achieved") : "-"), 2, 0);
                rowGrid.add(new Label(rs.getObject("points_achieved") != null ? String.valueOf(rs.getInt("points_achieved")) : "-"), 3, 0);
                int pos = rs.getInt("pos");
                int total = rs.getInt("total_students");
                rowGrid.add(new Label(rs.wasNull() ? "-" : pos + "/" + total), 4, 0);
                rowGrid.add(new Label(""), 5, 0);
                previewBox.getChildren().add(rowGrid);
                row++;
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(new Separator());

        // Summary
        VBox summary = new VBox(5);
        summary.setPadding(new Insets(8));
        summary.setStyle("-fx-background-color: #e8eaf6; -fx-background-radius: 6;");
        Label sumHeader = new Label("SUMMARY");
        sumHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        sumHeader.setTextFill(Color.web(PRIMARY));
        summary.getChildren().add(sumHeader);

        String sumSql = """
            SELECT ROUND(SUM(m.score), 1) AS total_marks,
                   COALESCE(SUM(m.points_achieved), 0) AS total_points,
                   ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points
            FROM marks m WHERE m.exam_id = ? AND m.student_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sumSql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                summary.getChildren().add(new Label("Total Marks: " + rs.getDouble("total_marks")
                    + " | Total Points: " + rs.getInt("total_points")
                    + " | Mean Points: " + rs.getDouble("mean_points")));
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }

        // Trend
        String trendSql = """
            SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m WHERE m.exam_id = ? AND m.student_id = ?
            """;
        String prevExamSql = """
            SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m JOIN exams e ON e.id = m.exam_id
            WHERE m.student_id = ? AND e.academic_year = (SELECT academic_year FROM exams WHERE id = ?) AND e.id < ?
            ORDER BY e.id DESC LIMIT 1
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(trendSql);
             PreparedStatement pps = conn.prepareStatement(prevExamSql)) {
            ps.setLong(1, examId); ps.setLong(2, studentId);
            ResultSet cr = ps.executeQuery();
            int currentPoints = cr.next() ? cr.getInt("total_points") : 0;

            pps.setLong(1, studentId); pps.setLong(2, examId); pps.setLong(3, examId);
            ResultSet pr = pps.executeQuery();
            int prevPoints = pr.next() ? pr.getInt("total_points") : -1;

            Label trend = new Label();
            if (prevPoints >= 0) {
                int diff = currentPoints - prevPoints;
                if (diff > 0) trend.setText("Trend: Improved by " + diff + " pts from previous exam");
                else if (diff < 0) trend.setText("Trend: Dropped by " + Math.abs(diff) + " pts from previous exam");
                else trend.setText("Trend: Maintained same points as previous exam");
            } else {
                trend.setText("Trend: First exam - no previous data.");
            }
            trend.setFont(Font.font("System", FontWeight.BOLD, 11));
            summary.getChildren().add(trend);
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(summary);
        previewBox.setVisible(true);
    }

    private void showAlert(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait()); }
    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait()); }
}
