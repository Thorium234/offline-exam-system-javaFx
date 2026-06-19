package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.Set;
import java.util.TreeSet;

public class StreamManagementForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final TableView<StreamRow> table = new TableView<>();
    private final ObservableList<StreamRow> data = FXCollections.observableArrayList();
    private final ComboBox<Integer> formBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
    private final ComboBox<String> streamBox = new ComboBox<>();
    private Button deleteBtn;
    private Label statusLabel;

    public StreamManagementForm(DatabaseEngine db) {
        this.db = db;
    }

    public Node getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = new Label("Stream Management");
        header.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.setTextFill(Color.web(PRIMARY));

        Label info = new Label("Add, edit, or remove streams (form–stream combinations).");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox addRow = new HBox(10);
        formBox.setPromptText("Form");
        formBox.setPrefWidth(100);
        streamBox.setPromptText("Stream Name");
        streamBox.setPrefWidth(200);
        streamBox.setEditable(true);
        loadStreamNames();
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
        deleteBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteBtn.setDisable(true);
        Button refreshBtn = new Button("Refresh");
        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setTextFill(Color.gray(0.5));
        actionRow.getChildren().addAll(deleteBtn, refreshBtn, statusLabel);

        VBox fields = new VBox(12);
        fields.setPadding(new Insets(15));
        fields.setStyle("-fx-background-color: white; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        fields.getChildren().addAll(addRow, table, actionRow);

        view.getChildren().addAll(header, info, fields);
        loadData();

        addBtn.setOnAction(e -> {
            if (formBox.getValue() == null) { showAlert("Select a form."); return; }
            String stream = streamBox.getValue();
            if (stream == null || stream.isBlank()) { showAlert("Enter a stream name."); return; }
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO streams (form, stream) VALUES (?, ?)")) {
                ps.setInt(1, formBox.getValue());
                ps.setString(2, stream.trim());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    loadData();
                    statusLabel.setText("Added Form " + formBox.getValue() + " " + stream.trim());
                } else {
                    showAlert("Stream already exists.");
                }
                formBox.setValue(null);
                streamBox.setValue(null);
            } catch (SQLException ex) { showAlert("Error: " + ex.getMessage()); }
        });

        deleteBtn.setOnAction(e -> {
            StreamRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete Form " + sel.form + " " + sel.streamName + "?\n"
                + sel.studentCount + " student(s) will be moved to stream 'General'.",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            try (Connection conn = db.getConnection();
                 PreparedStatement delStream = conn.prepareStatement(
                     "DELETE FROM streams WHERE form = ? AND stream = ?");
                 PreparedStatement updateStudents = conn.prepareStatement(
                     "UPDATE students SET stream = 'General' WHERE form = ? AND stream = ?")) {
                updateStudents.setInt(1, sel.form);
                updateStudents.setString(2, sel.streamName);
                updateStudents.executeUpdate();
                delStream.setInt(1, sel.form);
                delStream.setString(2, sel.streamName);
                delStream.executeUpdate();
                loadData();
                statusLabel.setText("Deleted " + sel.form + " " + sel.streamName);
            } catch (SQLException ex) { showAlert("Error: " + ex.getMessage()); }
        });

        refreshBtn.setOnAction(e -> loadData());

        return view;
    }

    private void loadStreamNames() {
        Set<String> names = new TreeSet<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) names.add(rs.getString("stream"));
        } catch (SQLException ignored) {}
        streamBox.setItems(FXCollections.observableArrayList(names));
    }

    private void loadData() {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT st.form, st.stream, "
                 + "(SELECT COUNT(*) FROM students s WHERE s.form = st.form AND s.stream = st.stream AND s.deallocated = 0) AS cnt "
                 + "FROM streams st ORDER BY st.form, st.stream")) {
            while (rs.next()) {
                data.add(new StreamRow(
                    rs.getInt("form"),
                    rs.getString("stream"),
                    rs.getInt("cnt")));
            }
        } catch (SQLException e) { showAlert("Error: " + e.getMessage()); }
        table.setItems(data);
        statusLabel.setText(data.size() + " stream(s)");
    }

    private void showAlert(String msg) {
        javafx.application.Platform.runLater(() ->
            new Alert(Alert.AlertType.ERROR, msg).showAndWait());
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
