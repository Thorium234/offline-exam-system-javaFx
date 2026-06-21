package com.zaraki.exams.forms;

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
import java.util.*;

public class TeacherAssignmentForm {

    private final DatabaseEngine db;
    private final ComboBox<String> teacherBox = new ComboBox<>();
    private final TableView<AssignmentRow> assignTable = new TableView<>();
    private final ObservableList<AssignmentRow> assignData = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();

    private final ComboBox<String> addSubjectBox = new ComboBox<>();
    private final ComboBox<Integer> addFormBox = new ComboBox<>();
    private final ComboBox<String> addStreamBox = new ComboBox<>();

    private long selectedUserId;

    public TeacherAssignmentForm(DatabaseEngine db) {
        this.db = db;
    }

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = new Label("Teacher Subject Assignment");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Assign subjects + form/stream combinations to teachers. This controls what subjects each teacher sees in Publish.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox teacherRow = new HBox(10);
        teacherRow.getChildren().addAll(new Label("Teacher:"), teacherBox);
        teacherBox.setPrefWidth(300);
        loadTeachers();
        teacherBox.setOnAction(e -> loadAssignments());

        // Existing assignments table
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

        // Add new assignment section
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

        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setTextFill(Color.gray(0.5));

        view.getChildren().addAll(header, info, teacherRow, assignTable, addLabel, addRow, statusLabel);
        return view;
    }

    private void loadTeachers() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, full_name FROM users WHERE role = 'teacher' ORDER BY full_name")) {
            while (rs.next())
                teacherBox.getItems().add(rs.getLong("id") + ":" + rs.getString("username") + " | " + rs.getString("full_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadAssignments() {
        assignData.clear();
        if (teacherBox.getValue() == null) return;
        selectedUserId = Long.parseLong(teacherBox.getValue().split(":")[0]);

        String sql = """
            SELECT ts.id, s.subject_name, ts.form, ts.stream
            FROM teacher_subjects ts
            JOIN subjects s ON s.id = ts.subject_id
            WHERE ts.user_id = ?
            ORDER BY s.subject_name, ts.form, ts.stream
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                assignData.add(new AssignmentRow(rs.getLong("id"), rs.getString("subject_name"),
                    rs.getInt("form"), rs.getString("stream")));
            statusLabel.setText(assignData.size() + " assignment(s)");
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void addAssignment() {
        if (selectedUserId == 0) { showAlert("Select a teacher first."); return; }
        if (addSubjectBox.getValue() == null) { showAlert("Select a subject."); return; }
        if (addFormBox.getValue() == null) { showAlert("Select a form."); return; }
        String stream = addStreamBox.getValue();
        if (stream == null || stream.isBlank()) { showAlert("Select a stream."); return; }

        long subjectId = Long.parseLong(addSubjectBox.getValue().split(":")[0]);

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR IGNORE INTO teacher_subjects (user_id, subject_id, form, stream) VALUES (?,?,?,?)")) {
            ps.setLong(1, selectedUserId);
            ps.setLong(2, subjectId);
            ps.setInt(3, addFormBox.getValue());
            ps.setString(4, stream.trim());
            int inserted = ps.executeUpdate();
            if (inserted > 0) {
                loadAssignments();
                addSubjectBox.setValue(null);
                addFormBox.setValue(null);
                addStreamBox.setValue(null);
            } else {
                showAlert("Assignment already exists.");
            }
        } catch (SQLException e) { showAlert("Failed to add: " + e.getMessage()); }
    }

    private void removeAssignment(AssignmentRow row) {
        if (row == null) return;
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM teacher_subjects WHERE id = ?")) {
            ps.setLong(1, row.id);
            ps.executeUpdate();
            loadAssignments();
        } catch (SQLException e) { showAlert("Failed to remove: " + e.getMessage()); }
    }

    private void loadSubjects() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_name FROM subjects ORDER BY subject_name")) {
            while (rs.next())
                addSubjectBox.getItems().add(rs.getLong("id") + ":" + rs.getString("subject_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadStreams() {
        Set<String> streams = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) streams.add(rs.getString("stream"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        addStreamBox.setItems(FXCollections.observableArrayList(streams));
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait());
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
