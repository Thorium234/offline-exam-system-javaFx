package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.IExcelService;
import com.zaraki.exams.service.ExcelServiceImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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
    private final IExcelService excelService;
    private final Stage stage;
    private final long loggedInUserId;
    private final boolean isTeacher;

    private TextArea logArea;
    private ProgressIndicator spinner;
    private ComboBox<String> examBox;
    private ComboBox<String> subjectBox;
    private ComboBox<Integer> formBox;
    private ComboBox<String> streamBox;
    private Label statusLabel;

    public BulkMarksForm(DatabaseEngine db, Stage stage, long loggedInUserId, String loggedInRole) {
        this.db = db;
        this.stage = stage;
        this.excelService = new ExcelServiceImpl();
        this.loggedInUserId = loggedInUserId;
        this.isTeacher = "teacher".equals(loggedInRole);
    }

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(10, 0, 10, 0));

        Label header = UIUtils.makeHeader("Bulk Marks");

        Label info = new Label(isTeacher
            ? "Select exam, subject, then stream to generate templates or upload filled sheets."
            : "Generate Excel templates per class, then upload the filled sheets to import marks.");

        VBox section1 = sectionBox("1. Select Exam & Class");
        HBox examRow = new HBox(10);
        examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        loadExams();
        examRow.getChildren().addAll(new Label("Exam:"), examBox);
        section1.getChildren().add(examRow);

        // Teacher-mode: Subject dropdown (filtered)
        if (isTeacher) {
            HBox subjRow = new HBox(10);
            subjectBox = new ComboBox<>();
            subjectBox.setPromptText("Select Subject");
            subjectBox.setPrefWidth(250);
            subjRow.getChildren().addAll(new Label("Subject:"), subjectBox);
            section1.getChildren().add(subjRow);
        }

        HBox classRow = new HBox(10);
        formBox = new ComboBox<>();
        loadFormsFromDb();
        formBox.setPromptText("Form");
        formBox.setPrefWidth(100);
        streamBox = new ComboBox<>();
        streamBox.setPromptText("Stream");
        streamBox.setPrefWidth(180);
        streamBox.setEditable(true);
        if (!isTeacher) loadStreams();
        classRow.getChildren().addAll(new Label("Class:"), formBox, streamBox);
        section1.getChildren().add(classRow);

        VBox section2 = sectionBox("2. Generate or Upload");
        HBox btnRow = new HBox(10);
        Button genBtn = new Button("Generate Template");
        genBtn.getStyleClass().addAll("button", "button-primary");
        Button uploadBtn = new Button("Upload Filled Sheet");
        uploadBtn.getStyleClass().addAll("button", "button-success");
        spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        Button generateAllBtn = new Button("Generate All Templates");
        generateAllBtn.getStyleClass().addAll("button", "button-danger");
        generateAllBtn.setVisible(false);

        Button uploadAllBtn = new Button("Upload All Sheets");
        uploadAllBtn.getStyleClass().addAll("button", "button-success");
        uploadAllBtn.setVisible(false);

        btnRow.getChildren().addAll(genBtn, uploadBtn, spinner);
        if (isTeacher) {
            btnRow.getChildren().addAll(generateAllBtn, uploadAllBtn);
            generateAllBtn.setVisible(true);
            uploadAllBtn.setVisible(true);
        }
        section2.getChildren().add(btnRow);

        statusLabel = UIUtils.makeStatusLabel();

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setPromptText("Results will appear here...");
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12;");

        view.getChildren().addAll(header, info, section1, section2, statusLabel, logArea);

        if (isTeacher) setupTeacherActions();

        genBtn.setOnAction(e -> {
            if (!validateSelection()) return;
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Excel Template");
            int form = formBox.getValue();
            String stream = streamBox.getValue();
            if (isTeacher) {
                String subjName = subjectBox.getValue().split(" - ", 2)[1];
                fc.setInitialFileName(subjName + "_form" + form + "_" + stream + ".xlsx");
            } else {
                fc.setInitialFileName("marks_template_form" + form + "_" + stream + ".xlsx");
            }
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            long examId = parseExamId();
            spinner.setVisible(true);
            statusLabel.setText("Generating template...");

            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    if (isTeacher) {
                        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
                        excelService.generateSubjectTemplate(file.toPath(), examId, form, stream, subjectId);
                    } else {
                        excelService.generateTemplate(file.toPath(), examId, form, stream);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Template saved to: " + file.getAbsolutePath());
                log("Template generated for Form " + form + " - " + stream);
                log("Exam: " + examBox.getValue());
                log("File: " + file.getAbsolutePath());
                if (isTeacher) log("Subject: " + subjectBox.getValue().split(" - ")[1]);
                log("Students: " + countStudents(form, stream));
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
            if (file.length() > 10_485_760) { UIUtils.showError("File too large. Maximum size is 10 MB."); return; }

            long examId = parseExamId();
            int form = formBox.getValue();
            String stream = streamBox.getValue();
            spinner.setVisible(true);
            statusLabel.setText("Processing upload...");

            Task<IExcelService.ImportResult> task = new Task<>() {
                @Override protected IExcelService.ImportResult call() {
                    if (isTeacher) {
                        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);
                        return excelService.processSubjectUpload(file.toPath(), examId, subjectId, form, stream);
                    }
                    return excelService.processUpload(file.toPath(), examId, form, stream);
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                IExcelService.ImportResult result = task.getValue();
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

        generateAllBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
            long examId = parseExamId();
            FileChooser fc = new FileChooser();
            fc.setTitle("Save All Templates as One Excel File");
            fc.setInitialFileName("all_subjects_templates.xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            spinner.setVisible(true);
            statusLabel.setText("Generating all templates...");

            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    excelService.generateTeacherMultiSheetTemplate(file.toPath(), examId, loggedInUserId);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("All templates saved to: " + file.getAbsolutePath());
                log("=== Multi-Sheet Template Generated ===");
                log("File: " + file.getAbsolutePath());
                log("All teacher subject/stream combos included.");
                log("---");
            });
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Generation failed.");
                log("ERROR: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        uploadAllBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
            long examId = parseExamId();
            int form = formBox.getValue();
            String stream = streamBox.getValue();
            FileChooser fc = new FileChooser();
            fc.setTitle("Upload Multi-Sheet Excel File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showOpenDialog(stage);
            if (file == null) return;
            if (file.length() > 10_485_760) { UIUtils.showError("File too large. Maximum size is 10 MB."); return; }

            spinner.setVisible(true);
            statusLabel.setText("Processing multi-sheet upload...");

            Task<IExcelService.ImportResult> task = new Task<>() {
                @Override protected IExcelService.ImportResult call() {
                    return excelService.processTeacherMultiSheetUpload(file.toPath(), examId, form, stream);
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                IExcelService.ImportResult result = task.getValue();
                statusLabel.setText("Import complete: " + result.marksInserted() + " marks inserted.");
                log("=== Multi-Sheet Import Results ===");
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

    private void setupTeacherActions() {
        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            loadTeacherSubjects();
        });

        subjectBox.setOnAction(e -> {
            if (subjectBox.getValue() == null) return;
            loadTeacherForms();
        });

        formBox.setOnAction(e -> {
            if (formBox.getValue() == null) return;
            loadTeacherStreams();
        });
    }

    private void loadTeacherSubjects() {
        subjectBox.getItems().clear();
        String sql = """
            SELECT DISTINCT s.id, s.subject_code, s.subject_name
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, loggedInUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                subjectBox.getItems().add(rs.getLong("id") + ":" + rs.getString("subject_code") + " - " + rs.getString("subject_name"));
        } catch (SQLException ex) { UIUtils.showError("Failed to load subjects: " + ex.getMessage()); }
    }

    private void loadTeacherForms() {
        formBox.setValue(null);
        streamBox.getItems().clear();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);

        Set<Integer> forms = new TreeSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT form FROM teacher_subjects WHERE user_id = ? AND subject_id = ? ORDER BY form")) {
            ps.setLong(1, loggedInUserId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (SQLException ex) { UIUtils.showError("Failed to load forms: " + ex.getMessage()); }
        formBox.setItems(FXCollections.observableArrayList(forms));
    }

    private void loadTeacherStreams() {
        streamBox.getItems().clear();
        int form = formBox.getValue();
        long subjectId = Long.parseLong(subjectBox.getValue().split(":")[0]);

        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT stream FROM teacher_subjects WHERE user_id = ? AND subject_id = ? AND form = ? ORDER BY stream")) {
            ps.setLong(1, loggedInUserId);
            ps.setLong(2, subjectId);
            ps.setInt(3, form);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException ex) { UIUtils.showError("Failed to load streams: " + ex.getMessage()); }

        if (streams.size() == 1) {
            streamBox.setItems(FXCollections.observableArrayList(streams));
            streamBox.setValue(streams.iterator().next());
        } else if (streams.size() > 1) {
            streamBox.setItems(FXCollections.observableArrayList(streams));
        } else {
            streamBox.setItems(FXCollections.observableArrayList());
        }
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
        if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return false; }
        if (isTeacher && (subjectBox.getValue() == null)) { UIUtils.showError("Select a subject."); return false; }
        if (formBox.getValue() == null) { UIUtils.showError("Select a form."); return false; }
        if (streamBox.getValue() == null || streamBox.getValue().isBlank()) {
            UIUtils.showError("Select or type a stream."); return false;
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
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
    }

    private void loadStreams() {
        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
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

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void loadFormsFromDb() {
        java.util.Set<Integer> forms = new java.util.TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM streams ORDER BY form")) {
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (Exception ignored) {}
        if (forms.isEmpty()) {
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM students WHERE deallocated = 0 ORDER BY form")) {
                while (rs.next()) forms.add(rs.getInt("form"));
            } catch (Exception ignored) {}
        }
        if (forms.isEmpty()) {
            for (int f = 1; f <= 4; f++) forms.add(f);
        }
        Integer current = formBox.getValue();
        formBox.setItems(FXCollections.observableArrayList(forms));
        if (current != null && forms.contains(current)) formBox.setValue(current);
    }


}
