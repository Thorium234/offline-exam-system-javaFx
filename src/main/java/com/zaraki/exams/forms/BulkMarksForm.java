package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExcelService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.util.Set;
import java.util.TreeSet;

public class BulkMarksForm {

    private final DatabaseEngine db;
    private final ExcelService excelService;
    private final Stage stage;

    private TextArea logArea;
    private ProgressIndicator spinner;
    private ComboBox<String> examBox;
    private ComboBox<Integer> formBox;
    private ComboBox<String> streamBox;
    private Label statusLabel;

    public BulkMarksForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.excelService = new ExcelService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(10, 0, 10, 0));

        Label header = new Label("Bulk Marks");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Generate Excel templates per class, then upload the filled sheets to import marks.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        VBox section1 = sectionBox("1. Select Exam & Class");
        HBox examRow = new HBox(10);
        examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        loadExams();
        examRow.getChildren().addAll(new Label("Exam:"), examBox);
        section1.getChildren().add(examRow);

        HBox classRow = new HBox(10);
        formBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Form");
        formBox.setPrefWidth(100);
        streamBox = new ComboBox<>();
        streamBox.setPromptText("Stream");
        streamBox.setPrefWidth(180);
        streamBox.setEditable(true);
        loadStreams();
        classRow.getChildren().addAll(new Label("Class:"), formBox, streamBox);
        section1.getChildren().add(classRow);

        VBox section2 = sectionBox("2. Generate or Upload");
        HBox btnRow = new HBox(10);
        Button genBtn = new Button("Generate Template");
        genBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white; -fx-font-weight: bold;");
        Button uploadBtn = new Button("Upload Filled Sheet");
        uploadBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);
        btnRow.getChildren().addAll(genBtn, uploadBtn, spinner);
        section2.getChildren().add(btnRow);

        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.gray(0.4));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setPromptText("Results will appear here...");
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12;");

        view.getChildren().addAll(header, info, section1, section2, statusLabel, logArea);

        genBtn.setOnAction(e -> {
            if (!validateSelection()) return;
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Excel Template");
            fc.setInitialFileName("marks_template_form" + formBox.getValue() + "_" + streamBox.getValue() + ".xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            long examId = parseExamId();
            int form = formBox.getValue();
            String stream = streamBox.getValue();
            spinner.setVisible(true);
            statusLabel.setText("Generating template...");

            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    excelService.generateTemplate(file.toPath(), examId, form, stream);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Template saved to: " + file.getAbsolutePath());
                log("Template generated for Form " + form + " - " + stream);
                log("Exam: " + examBox.getValue());
                log("File: " + file.getAbsolutePath());
                log("Students: " + countStudents(form, stream) + " | Subjects: " + countSubjects());
                log("---");
            });
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Generation failed.");
                log("ERROR: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        uploadBtn.setOnAction(e -> {
            if (!validateSelection()) return;
            FileChooser fc = new FileChooser();
            fc.setTitle("Upload Filled Excel Sheet");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showOpenDialog(stage);
            if (file == null) return;

            long examId = parseExamId();
            spinner.setVisible(true);
            statusLabel.setText("Processing upload...");

            Task<ExcelService.ImportResult> task = new Task<>() {
                @Override protected ExcelService.ImportResult call() {
                    return excelService.processUpload(file.toPath(), examId);
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                ExcelService.ImportResult result = task.getValue();
                statusLabel.setText("Import complete: " + result.marksInserted() + " marks inserted.");
                log("=== Import Results ===");
                log("File: " + file.getName());
                log("Rows processed: " + result.totalRows());
                log("Marks inserted: " + result.marksInserted());
                log("Errors: " + result.errors());
                for (String err : result.errorMessages()) {
                    log("  ERROR: " + err);
                }
                log("---");
            });
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Import failed.");
                log("ERROR: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        return view;
    }

    private VBox sectionBox(String title) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12, 15, 12, 15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);");
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        box.getChildren().add(lbl);
        return box;
    }

    private boolean validateSelection() {
        if (examBox.getValue() == null) { showAlert("Select an exam."); return false; }
        if (formBox.getValue() == null) { showAlert("Select a form."); return false; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) {
            showAlert("Select or type a stream."); return false;
        }
        return true;
    }

    private long parseExamId() {
        return Long.parseLong(examBox.getValue().split(" - ")[0]);
    }

    private void loadExams() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadStreams() {
        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM students ORDER BY stream")) {
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        streamBox.setItems(FXCollections.observableArrayList(streams));
    }

    private int countStudents(int form, String stream) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM students WHERE form = ? AND stream = ? AND deallocated = 0")) {
            ps.setInt(1, form); ps.setString(2, stream);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private int countSubjects() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM subjects")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }
}
