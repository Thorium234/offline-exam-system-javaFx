package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.service.ExcelService;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class StudentForm {

    private final DatabaseEngine db;
    private final ExcelService excelService;
    private final Stage stage;

    private TableView<StudentRow> table;
    private ObservableList<StudentRow> data;
    private Label statusLabel;

    public StudentForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.excelService = new ExcelService();
    }

    public VBox getView() {
        VBox view = new VBox(15);

        Label header = UIUtils.makeHeader("Students");

        Label info = new Label("Add students manually or use Excel template for bulk import.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox form = new HBox(10);
        TextField admField = new TextField(); admField.setPromptText("Admission No.");
        TextField nameField = new TextField(); nameField.setPromptText("Full Name");
        TextField formField = new TextField(); formField.setPromptText("Form");
        TextField streamField = new TextField(); streamField.setPromptText("Stream");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(admField, nameField, formField, streamField, addBtn);

        HBox excelRow = new HBox(10);
        Button genBtn = new Button("Generate Template");
        Button uploadBtn = new Button("Upload Filled Sheet");
        Button exportPdfBtn = new Button("Export PDF");
        Button exportExcelBtn = new Button("Export Excel");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(20, 20);
        excelRow.getChildren().addAll(genBtn, uploadBtn, exportPdfBtn, exportExcelBtn, spinner);

        statusLabel = UIUtils.makeStatusLabel();

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<StudentRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id")); colId.setPrefWidth(60);
        TableColumn<StudentRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission")); colAdm.setPrefWidth(140);
        TableColumn<StudentRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name")); colName.setPrefWidth(250);
        TableColumn<StudentRow, Integer> colForm = new TableColumn<>("Form");
        colForm.setCellValueFactory(new PropertyValueFactory<>("form")); colForm.setPrefWidth(70);
        TableColumn<StudentRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("stream")); colStream.setPrefWidth(120);

        TableColumn<StudentRow, Void> colPhoto = new TableColumn<>("Photo");
        colPhoto.setPrefWidth(80);
        colPhoto.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Upload");
            { btn.setStyle("-fx-font-size: 10;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    StudentRow row = getTableRow().getItem();
                    btn.setOnAction(e -> uploadPhoto(row));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<StudentRow, Void> colSubj = new TableColumn<>("Subjects");
        colSubj.setPrefWidth(90);
        colSubj.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Manage");
            { btn.setStyle("-fx-font-size: 10;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    StudentRow row = getTableRow().getItem();
                    btn.setOnAction(e -> manageSubjects(row));
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(colId, colAdm, colName, colForm, colStream, colPhoto, colSubj);
        table.setPrefHeight(400);

        data = FXCollections.observableArrayList();
        load();
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)")) {
                String adm = admField.getText().trim();
                String name = nameField.getText().trim();
                String formText = formField.getText().trim();
                String stream = streamField.getText().trim();
                if (adm.isEmpty() || name.isEmpty() || formText.isEmpty() || stream.isEmpty()) {
                    UIUtils.showError("All fields are required.");
                    return;
                }
                int formNum;
                try {
                    formNum = Integer.parseInt(formText);
                    if (formNum < 1 || formNum > 4) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    UIUtils.showError("Form must be a number between 1 and 4.");
                    return;
                }
                ps.setString(1, adm);
                ps.setString(2, name);
                ps.setInt(3, formNum);
                ps.setString(4, stream);
                ps.executeUpdate();
                load();
                admField.clear(); nameField.clear(); formField.clear(); streamField.clear();
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        genBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Student Template");
            fc.setInitialFileName("student_template.xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    excelService.generateStudentTemplate(file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Template saved to: " + file.getName()); });
            task.setOnFailed(ev -> { spinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        uploadBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Upload Students Excel");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showOpenDialog(stage);
            if (file == null) return;
            if (file.length() > 10_485_760) { UIUtils.showError("File too large. Maximum size is 10 MB."); return; }
            spinner.setVisible(true);
            Task<ExcelService.StudentImportResult> task = new Task<>() {
                @Override protected ExcelService.StudentImportResult call() {
                    return excelService.processStudentUpload(file.toPath());
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                ExcelService.StudentImportResult r = task.getValue();
                statusLabel.setText("Imported: " + r.inserted() + " new, " + r.updated() + " updated, " + r.errors() + " errors");
                load();
            });
            task.setOnFailed(ev -> { spinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        exportPdfBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Student List as PDF");
            fc.setInitialFileName("student_list.pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    new ReportCardGenerator().generateStudentListPdf("", "", file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("PDF saved: " + file.getName()); });
            task.setOnFailed(ev -> { spinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        exportExcelBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Student List as Excel");
            fc.setInitialFileName("student_list.xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    new ExcelService().generateStudentListExcel(file.toPath(), "", "");
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Excel saved: " + file.getName()); });
            task.setOnFailed(ev -> { spinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        view.getChildren().addAll(header, info, form, excelRow, statusLabel, table);
        return view;
    }

    private void load() {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
              ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name, form, stream FROM students WHERE deallocated = 0")) {
            while (rs.next())
                data.add(new StudentRow(rs.getLong("id"), rs.getString("admission_number"),
                    rs.getString("full_name"), rs.getInt("form"), rs.getString("stream")));
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }
    }

    private void uploadPhoto(StudentRow row) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Photo for " + row.name);
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        if (file.length() > 2_097_152) { UIUtils.showError("Photo too large. Max 2 MB."); return; }
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                byte[] bytes = Files.readAllBytes(file.toPath());
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement("UPDATE students SET photo = ? WHERE id = ?")) {
                    ps.setBytes(1, bytes);
                    ps.setLong(2, row.id);
                    ps.executeUpdate();
                }
                return null;
            }
        };
        task.setOnSucceeded(ev -> statusLabel.setText("Photo saved for " + row.name));
        task.setOnFailed(ev -> UIUtils.showError(task.getException().getMessage()));
        new Thread(task).start();
    }

    private void manageSubjects(StudentRow row) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Subjects for " + row.name);
        dialog.setHeaderText("Select subjects for " + row.name + " (Adm: " + row.admission + ")");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(5);
        content.setPadding(new Insets(10));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        content.getChildren().add(spinner);

        dialog.getDialogPane().setContent(content);

        Map<Long, CheckBox> subjBoxes = new LinkedHashMap<>();
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                Set<Long> enrolled = new HashSet<>();
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT subject_id FROM student_subjects WHERE student_id = ?")) {
                    ps.setLong(1, row.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) enrolled.add(rs.getLong("subject_id"));
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }

                ObservableList<SubjectInfo> subjects = FXCollections.observableArrayList();
                try (Connection conn = db.getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, subject_code, subject_name FROM subjects ORDER BY subject_name")) {
                    while (rs.next())
                        subjects.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
                } catch (SQLException e) { throw new RuntimeException(e); }

                javafx.application.Platform.runLater(() -> {
                    content.getChildren().clear();
                    for (SubjectInfo si : subjects) {
                        CheckBox cb = new CheckBox(si.code + " - " + si.name);
                        cb.setSelected(enrolled.contains(si.id));
                        subjBoxes.put(si.id, cb);
                        content.getChildren().add(cb);
                    }
                    Button saveBtn = new Button("Save");
                    saveBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
                    saveBtn.setOnAction(e -> saveSubjects(row.id, subjBoxes, dialog));
                    content.getChildren().add(saveBtn);
                });
                return null;
            }
        };
        task.setOnFailed(ev -> UIUtils.showError(task.getException().getMessage()));
        new Thread(task).start();
        dialog.showAndWait();
    }

    private void saveSubjects(long studentId, Map<Long, CheckBox> boxes, Dialog<?> dialog) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                try (Connection conn = db.getConnection();
                     PreparedStatement del = conn.prepareStatement("DELETE FROM student_subjects WHERE student_id = ?");
                     PreparedStatement ins = conn.prepareStatement(
                         "INSERT OR IGNORE INTO student_subjects (student_id, subject_id) VALUES (?,?)")) {
                    del.setLong(1, studentId);
                    del.executeUpdate();
                    for (var entry : boxes.entrySet()) {
                        if (entry.getValue().isSelected()) {
                            ins.setLong(1, studentId);
                            ins.setLong(2, entry.getKey());
                            ins.executeUpdate();
                        }
                    }
                } catch (SQLException e) { throw new RuntimeException(e); }
                return null;
            }
        };
        task.setOnSucceeded(ev -> {
            statusLabel.setText("Subjects saved for student");
            dialog.close();
        });
        task.setOnFailed(ev -> UIUtils.showError(task.getException().getMessage()));
        new Thread(task).start();
    }

    private record SubjectInfo(long id, String code, String name) {}

    public static class StudentRow {
        private final Long id; private final String admission, name; private final Integer form; private final String stream;
        public StudentRow(Long id, String admission, String name, Integer form, String stream) {
            this.id = id; this.admission = admission; this.name = name; this.form = form; this.stream = stream;
        }
        public Long getId() { return id; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Integer getForm() { return form; }
        public String getStream() { return stream; }
    }
}
