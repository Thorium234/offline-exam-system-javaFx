package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.repository.IStudentRepository;
import com.zaraki.exams.repository.ISubjectRepository;
import com.zaraki.exams.repository.StudentRepositoryImpl;
import com.zaraki.exams.repository.SubjectRepositoryImpl;
import com.zaraki.exams.service.IExcelService;
import com.zaraki.exams.service.ExcelServiceImpl;
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
import java.util.*;
import java.util.stream.Collectors;

public class StudentForm {

    private final DatabaseEngine db;
    private final IStudentRepository studentRepo;
    private final ISubjectRepository subjectRepo;
    private final IExcelService excelService;
    private final Stage stage;

    private TableView<StudentRow> table;
    private ObservableList<StudentRow> data;
    private Label statusLabel;

    public StudentForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.studentRepo = new StudentRepositoryImpl();
        this.subjectRepo = new SubjectRepositoryImpl();
        this.excelService = new ExcelServiceImpl();
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

        TableColumn<StudentRow, Void> colEdit = new TableColumn<>("Edit");
        colEdit.setPrefWidth(60);
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    StudentRow row = getTableRow().getItem();
                    btn.setOnAction(e -> editStudent(row));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<StudentRow, Void> colDel = new TableColumn<>("");
        colDel.setPrefWidth(70);
        colDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Delete");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    StudentRow row = getTableRow().getItem();
                    btn.setOnAction(e -> deleteStudent(row));
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(colId, colAdm, colName, colForm, colStream, colPhoto, colSubj, colEdit, colDel);
        table.setPrefHeight(400);

        data = FXCollections.observableArrayList();
        load();
        table.setItems(data);

        addBtn.setOnAction(e -> {
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
            try {
                studentRepo.insert(adm, name, formNum, stream);
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
            Task<IExcelService.StudentImportResult> task = new Task<>() {
                @Override protected IExcelService.StudentImportResult call() {
                    return excelService.processStudentUpload(file.toPath());
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                IExcelService.StudentImportResult r = task.getValue();
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
                    new ExcelServiceImpl().generateStudentListExcel(file.toPath(), "", "");
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
        try {
            var students = studentRepo.findAllActive();
            for (var s : students)
                data.add(new StudentRow(
                    (Long) s.get("id"),
                    (String) s.get("admission_number"),
                    (String) s.get("full_name"),
                    (Integer) s.get("form"),
                    (String) s.get("stream")));
        } catch (Exception e) { UIUtils.showError(e.getMessage()); }
    }

    private void editStudent(StudentRow row) {
        Dialog<StudentRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Student");
        dialog.setHeaderText("Edit " + row.getName() + " (" + row.getAdmission() + ")");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField admField = new TextField(row.getAdmission());
        TextField nameField = new TextField(row.getName());
        TextField formField = new TextField(String.valueOf(row.getForm()));
        TextField streamField = new TextField(row.getStream());

        grid.add(new Label("Admission:"), 0, 0); grid.add(admField, 1, 0);
        grid.add(new Label("Name:"), 0, 1); grid.add(nameField, 1, 1);
        grid.add(new Label("Form:"), 0, 2); grid.add(formField, 1, 2);
        grid.add(new Label("Stream:"), 0, 3); grid.add(streamField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogBtn -> {
            if (dialogBtn == saveType) {
                String adm = admField.getText().trim();
                String name = nameField.getText().trim();
                String fText = formField.getText().trim();
                String stream = streamField.getText().trim();
                if (adm.isEmpty() || name.isEmpty() || fText.isEmpty() || stream.isEmpty()) {
                    UIUtils.showError("All fields are required.");
                    return null;
                }
                try {
                    int form = Integer.parseInt(fText);
                    if (form < 1 || form > 4) throw new NumberFormatException();
                    return new StudentRow(row.getId(), adm, name, form, stream);
                } catch (NumberFormatException ex) {
                    UIUtils.showError("Form must be 1-4.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                studentRepo.update(result.getId(), result.getAdmission(), result.getName(),
                    result.getForm(), result.getStream());
                load();
                UIUtils.showInfo("Student updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteStudent(StudentRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Deallocate student '" + row.getName() + "' (" + row.getAdmission() + ")?\n"
            + "They will be moved to the Recycle Bin.",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            studentRepo.deallocate(row.getId());
            load();
            UIUtils.showInfo("Student deallocated. Use Recycle Bin to restore.");
        } catch (Exception ex) { UIUtils.showError("Error: " + ex.getMessage()); }
    }

    private void uploadPhoto(StudentRow row) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Photo for " + row.name);
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        if (file.length() > 2_097_152) { UIUtils.showError("Photo too large. Max 2 MB."); return; }
        String name = file.getName().toLowerCase();
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            UIUtils.showError("Only PNG and JPEG images are supported.");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                byte[] bytes = Files.readAllBytes(file.toPath());
                studentRepo.updatePhoto(row.id, bytes);
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
                Set<Long> enrolled = studentRepo.getEnrolledSubjectIds(row.id);
                var subjects = subjectRepo.findAllSimple();

                javafx.application.Platform.runLater(() -> {
                    content.getChildren().clear();
                    for (var si : subjects) {
                        Long sid = (Long) si.get("id");
                        CheckBox cb = new CheckBox(si.get("subject_code") + " - " + si.get("subject_name"));
                        cb.setSelected(enrolled.contains(sid));
                        subjBoxes.put(sid, cb);
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
                Map<Long, Boolean> selections = new LinkedHashMap<>();
                for (var entry : boxes.entrySet())
                    selections.put(entry.getKey(), entry.getValue().isSelected());
                studentRepo.saveSubjects(studentId, selections);
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
