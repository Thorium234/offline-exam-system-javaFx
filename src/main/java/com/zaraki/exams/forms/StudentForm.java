package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.service.ExcelService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;

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

        Label header = new Label("Students");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

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

        statusLabel = new Label();
        statusLabel.setTextFill(Color.gray(0.5));

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
        table.getColumns().addAll(colId, colAdm, colName, colForm, colStream);
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
                    showAlert("All fields are required.");
                    return;
                }
                int formNum;
                try {
                    formNum = Integer.parseInt(formText);
                    if (formNum < 1 || formNum > 4) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    showAlert("Form must be a number between 1 and 4.");
                    return;
                }
                ps.setString(1, adm);
                ps.setString(2, name);
                ps.setInt(3, formNum);
                ps.setString(4, stream);
                ps.executeUpdate();
                load();
                admField.clear(); nameField.clear(); formField.clear(); streamField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
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
            task.setOnFailed(ev -> { spinner.setVisible(false); showAlert(task.getException().getMessage()); });
            new Thread(task).start();
        });

        uploadBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Upload Students Excel");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showOpenDialog(stage);
            if (file == null) return;
            if (file.length() > 10_485_760) { showAlert("File too large. Maximum size is 10 MB."); return; }
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
            task.setOnFailed(ev -> { spinner.setVisible(false); showAlert(task.getException().getMessage()); });
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
            task.setOnFailed(ev -> { spinner.setVisible(false); showAlert(task.getException().getMessage()); });
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
            task.setOnFailed(ev -> { spinner.setVisible(false); showAlert(task.getException().getMessage()); });
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
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }

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
