package com.zaraki.exams.forms;

import com.zaraki.exams.repository.ISubjectRepository;
import com.zaraki.exams.repository.ITeacherSubjectRepository;
import com.zaraki.exams.repository.IStreamRepository;
import com.zaraki.exams.repository.IUserRepository;
import com.zaraki.exams.repository.SubjectRepositoryImpl;
import com.zaraki.exams.repository.TeacherSubjectRepositoryImpl;
import com.zaraki.exams.repository.StreamRepositoryImpl;
import com.zaraki.exams.repository.UserRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

public class TeacherAssignmentForm {

    private final ITeacherSubjectRepository tsRepo = new TeacherSubjectRepositoryImpl();
    private final IUserRepository userRepo = new UserRepositoryImpl();
    private final ISubjectRepository subjectRepo = new SubjectRepositoryImpl();
    private final IStreamRepository streamRepo = new StreamRepositoryImpl();
    private final ComboBox<String> teacherBox = new ComboBox<>();
    private final TableView<AssignmentRow> assignTable = new TableView<>();
    private final ObservableList<AssignmentRow> assignData = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();

    private final ComboBox<String> addSubjectBox = new ComboBox<>();
    private final ComboBox<Integer> addFormBox = new ComboBox<>();
    private final ComboBox<String> addStreamBox = new ComboBox<>();

    private long selectedUserId;

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("Teacher Subject Assignment");

        Label info = new Label("Assign subjects + form/stream combinations to teachers. This controls what subjects each teacher sees in Publish.");

        HBox teacherRow = new HBox(10);
        teacherRow.getChildren().addAll(new Label("Teacher:"), teacherBox);
        teacherBox.setPrefWidth(300);
        loadTeachers();
        teacherBox.setOnAction(e -> loadAssignments());

        assignTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        assignTable.setItems(assignData);
        assignTable.setPrefHeight(250);

        TableColumn<AssignmentRow, String> cSubj = new TableColumn<>("Subject");
        cSubj.setCellValueFactory(new PropertyValueFactory<>("subjectName")); cSubj.setPrefWidth(160);
        TableColumn<AssignmentRow, Integer> cForm = new TableColumn<>("Form");
        cForm.setCellValueFactory(new PropertyValueFactory<>("form")); cForm.setPrefWidth(60);
        TableColumn<AssignmentRow, String> cStream = new TableColumn<>("Stream");
        cStream.setCellValueFactory(new PropertyValueFactory<>("stream")); cStream.setPrefWidth(80);
        TableColumn<AssignmentRow, Void> cAction = new TableColumn<>("Action");
        cAction.setPrefWidth(100);
        cAction.setCellFactory(col -> new TableCell<>() {
            private final Button delBtn = new Button("Remove");
            { delBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-size: 11;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                AssignmentRow row = (AssignmentRow) getTableRow().getItem();
                Button btn = new Button("Remove");
                btn.setStyle(delBtn.getStyle());
                btn.setOnAction(e -> removeAssignment(row));
                setGraphic(btn);
            }
        });
        assignTable.getColumns().addAll(cSubj, cForm, cStream, cAction);

        Label addLabel = new Label("Add Assignment");
        addLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox addRow = new HBox(10);
        addSubjectBox.setPromptText("Subject");
        addSubjectBox.setPrefWidth(200);
        loadSubjects();
        addFormBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        addFormBox.setPromptText("Form");
        addStreamBox.setPromptText("Stream");
        addStreamBox.setPrefWidth(120);
        addStreamBox.setEditable(true);
        loadStreams();
        Button addBtn = new Button("Add");
        addBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> addAssignment());
        addRow.getChildren().addAll(addSubjectBox, addFormBox, addStreamBox, addBtn);

        view.getChildren().addAll(header, info, teacherRow, assignTable, addLabel, addRow, statusLabel);
        return view;
    }

    private void loadTeachers() {
        List<Map<String, Object>> teachers = userRepo.findAll();
        teacherBox.getItems().clear();
        for (Map<String, Object> t : teachers) {
            if ("teacher".equals(t.get("role"))) {
                teacherBox.getItems().add(t.get("id") + ":" + t.get("username") + " | " + t.get("full_name"));
            }
        }
    }

    private void loadAssignments() {
        assignData.clear();
        if (teacherBox.getValue() == null) return;
        selectedUserId = Long.parseLong(teacherBox.getValue().split(":")[0]);

        List<Map<String, Object>> list = tsRepo.findByUserId(selectedUserId);
        for (Map<String, Object> row : list)
            assignData.add(new AssignmentRow((long) row.get("id"), (String) row.get("subject_name"),
                (int) row.get("form"), (String) row.get("stream")));
        statusLabel.setText(assignData.size() + " assignment(s)");
    }

    private void addAssignment() {
        if (selectedUserId == 0) { UIUtils.showError("Select a teacher first."); return; }
        if (addSubjectBox.getValue() == null) { UIUtils.showError("Select a subject."); return; }
        if (addFormBox.getValue() == null) { UIUtils.showError("Select a form."); return; }
        String stream = addStreamBox.getValue();
        if (stream == null || stream.isBlank()) { UIUtils.showError("Select a stream."); return; }

        long subjectId = Long.parseLong(addSubjectBox.getValue().split(":")[0]);

        try {
            tsRepo.insert(selectedUserId, subjectId, addFormBox.getValue(), stream.trim());
            loadAssignments();
            addSubjectBox.setValue(null);
            addFormBox.setValue(null);
            addStreamBox.setValue(null);
        } catch (Exception e) {
            UIUtils.showError("Failed to add: " + e.getMessage());
        }
    }

    private void removeAssignment(AssignmentRow row) {
        if (row == null) return;
        try {
            tsRepo.deleteById(row.id);
            loadAssignments();
        } catch (Exception e) {
            UIUtils.showError("Failed to remove: " + e.getMessage());
        }
    }

    private void loadSubjects() {
        List<Map<String, Object>> subjects = subjectRepo.findAllSimple();
        addSubjectBox.getItems().clear();
        for (Map<String, Object> s : subjects)
            addSubjectBox.getItems().add(s.get("id") + ":" + s.get("subject_name"));
    }

    private void loadStreams() {
        Set<String> streams = streamRepo.findAllNames();
        addStreamBox.setItems(FXCollections.observableArrayList(streams));
    }

    public static class AssignmentRow {
        private final long id;
        private final String subjectName;
        private final int form;
        private final String stream;
        public AssignmentRow(long id, String subjectName, int form, String stream) {
            this.id = id; this.subjectName = subjectName; this.form = form; this.stream = stream;
        }
        public long getId() { return id; }
        public String getSubjectName() { return subjectName; }
        public int getForm() { return form; }
        public String getStream() { return stream; }
    }
}
