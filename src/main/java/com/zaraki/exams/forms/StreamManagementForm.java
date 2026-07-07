package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.IStreamRepository;
import com.zaraki.exams.repository.StreamRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import static com.zaraki.exams.forms.AppTheme.PRIMARY;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.*;

public class StreamManagementForm {

    private final IStreamRepository streamRepo;
    private final TableView<StreamRow> table = new TableView<>();
    private final ObservableList<StreamRow> data = FXCollections.observableArrayList();
    private final ComboBox<Integer> formBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private Button deleteBtn;
    private Label statusLabel;

    public StreamManagementForm(DatabaseEngine db) {
        this.streamRepo = new StreamRepositoryImpl();
    }

    public Node getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("Stream Management");

        Label info = new Label("Add, edit, or remove streams (form–stream combinations).");

        HBox addRow = new HBox(10);
        formBox.setPromptText("Form");
        formBox.setPrefWidth(100);
        streamBox.setPromptText("Stream Name");
        streamBox.setPrefWidth(200);
        streamBox.setEditable(true);
        loadStreamNames();
        loadStreamForms();
        Button addBtn = new Button("Add Stream");
        addBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white; -fx-font-weight: bold;");
        addRow.getChildren().addAll(new Label("Form:"), formBox, new Label("Stream:"), streamBox, addBtn);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);

        TableColumn<StreamRow, Integer> colForm = new TableColumn<>("Form");
        colForm.setCellValueFactory(new PropertyValueFactory<>("form"));
        colForm.setPrefWidth(100);

        TableColumn<StreamRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("streamName"));
        colStream.setPrefWidth(200);

        TableColumn<StreamRow, Integer> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(new PropertyValueFactory<>("studentCount"));
        colCount.setPrefWidth(100);

        table.getColumns().addAll(colForm, colStream, colCount);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) ->
            deleteBtn.setDisable(sel == null));

        HBox actionRow = new HBox(10);
        deleteBtn = new Button("Delete Selected");
        deleteBtn.getStyleClass().addAll("button", "button-danger");
        deleteBtn.setDisable(true);
        Button refreshBtn = new Button("Refresh");
        statusLabel = UIUtils.makeStatusLabel();
        actionRow.getChildren().addAll(deleteBtn, refreshBtn, statusLabel);

        VBox fields = new VBox(12);
        fields.setPadding(new Insets(15));
        fields.setStyle("-fx-background-color: white; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        fields.getChildren().addAll(addRow, table, actionRow);

        view.getChildren().addAll(header, info, fields);
        refreshAll();

        addBtn.setOnAction(e -> {
            if (formBox.getValue() == null) { UIUtils.showError("Select a form."); return; }
            String stream = streamBox.getValue();
            if (stream == null || stream.isBlank()) { UIUtils.showError("Enter a stream name."); return; }
            try {
                streamRepo.insert(formBox.getValue(), stream.trim());
                refreshAll();
                statusLabel.setText("Added Form " + formBox.getValue() + " " + stream.trim());
                formBox.setValue(null);
                streamBox.setValue(null);
            } catch (Exception ex) { UIUtils.showError("Error: " + ex.getMessage()); }
        });

        deleteBtn.setOnAction(e -> {
            StreamRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete Form " + sel.form + " " + sel.streamName + "?\n"
                + sel.studentCount + " student(s) will be moved to stream 'General'.",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            try {
                streamRepo.updateStudentsStreamToGeneral(sel.form, sel.streamName);
                streamRepo.delete(sel.form, sel.streamName);
                refreshAll();
                statusLabel.setText("Deleted " + sel.form + " " + sel.streamName);
            } catch (Exception ex) { UIUtils.showError("Error: " + ex.getMessage()); }
        });

        refreshBtn.setOnAction(e -> refreshAll());

        return view;
    }

    private void loadStreamNames() {
        // Query streams table first, fallback to students
        Set<String> names = new TreeSet<>();
        try (Connection conn = DatabaseEngine.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
                while (rs.next()) names.add(rs.getString("stream"));
            }
            if (names.isEmpty()) {
                try (ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM students WHERE deallocated = 0 ORDER BY stream")) {
                    while (rs.next()) names.add(rs.getString("stream"));
                }
            }
        } catch (Exception ignored) {}
        if (names.isEmpty()) {
            names.add("A"); names.add("B"); names.add("C"); names.add("D"); names.add("E");
        }
        streamBox.setItems(FXCollections.observableArrayList(names));
    }

    private void loadStreamForms() {
        // Query streams table first, fallback to students, then fallback to defaults
        Set<Integer> forms = new TreeSet<>();
        try (Connection conn = DatabaseEngine.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM streams ORDER BY form")) {
                while (rs.next()) forms.add(rs.getInt("form"));
            }
            if (forms.isEmpty()) {
                try (ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM students WHERE deallocated = 0 ORDER BY form")) {
                    while (rs.next()) forms.add(rs.getInt("form"));
                }
            }
        } catch (Exception ignored) {}
        if (forms.isEmpty()) {
            for (int f = 1; f <= 4; f++) forms.add(f);
        }
        ComboBox<Integer> box = formBox;
        Integer current = box.getValue();
        box.setItems(FXCollections.observableArrayList(forms));
        if (current != null && forms.contains(current)) box.setValue(current);
    }

    private void refreshAll() {
        loadData();
        loadStreamNames();
        loadStreamForms();
    }

    private void loadData() {
        data.clear();
        try {
            // Query streams table with student counts via repository
            var streams = streamRepo.findAllWithStudentCount();
            if (streams.isEmpty()) {
                // Fallback: query students directly as streams table may be empty
                streams = new java.util.ArrayList<>();
                try (Connection conn = DatabaseEngine.getInstance().getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT form, stream, COUNT(*) AS cnt FROM students WHERE deallocated = 0 GROUP BY form, stream ORDER BY form, stream")) {
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("form", rs.getInt("form"));
                        row.put("stream", rs.getString("stream"));
                        row.put("cnt", rs.getInt("cnt"));
                        streams.add(row);
                    }
                } catch (Exception ignored) {}
            }
            for (var s : streams)
                data.add(new StreamRow((int) s.get("form"), (String) s.get("stream"), (int) s.get("cnt")));
            if (data.isEmpty()) {
                table.setPlaceholder(new EmptyStatePlaceholder("No streams defined yet. Add a stream above.", "\uD83D\uDCC1").getView());
            }
        } catch (Exception e) { UIUtils.showError("Error: " + e.getMessage()); }
        table.setItems(data);
        statusLabel.setText(data.size() + " stream(s)");
    }

    public static class StreamRow {
        private final int form;
        private final String streamName;
        private final int studentCount;
        public StreamRow(int form, String streamName, int studentCount) {
            this.form = form; this.streamName = streamName; this.studentCount = studentCount;
        }
        public int getForm() { return form; }
        public String getStreamName() { return streamName; }
        public int getStudentCount() { return studentCount; }
    }
}
