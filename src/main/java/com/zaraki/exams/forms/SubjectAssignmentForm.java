package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SubjectAssignmentForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final StackPane root;
    private final Map<Long, CheckBox> subjectCheckBoxes = new LinkedHashMap<>();

    public SubjectAssignmentForm(DatabaseEngine db) {
        this.db = db;
        this.root = new StackPane();
        showAssignment();
    }

    public Node getView() { return root; }
    private void setView(Node node) { root.getChildren().setAll(node); }

    private void showAssignment() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = new Label("Stream Subject Assignment");
        header.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.setTextFill(Color.web(PRIMARY));

        HBox controls = new HBox(15);
        ComboBox<Integer> formBox = new ComboBox<>();
        formBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Select Form");
        formBox.setPrefWidth(150);

        ComboBox<String> streamBox = new ComboBox<>();
        streamBox.setEditable(true);
        streamBox.setPromptText("Stream (e.g. A)");
        streamBox.setPrefWidth(150);

        Button loadBtn = new Button("Load Subjects");
        loadBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white;");

        controls.getChildren().addAll(new Label("Form:"), formBox, new Label("Stream:"), streamBox, loadBtn);

        VBox subjectList = new VBox(5);
        subjectList.setPadding(new Insets(10));
        subjectList.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8;");

        Button saveBtn = new Button("Save Assignments");
        saveBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setDisable(true);

        Label statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 12));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        loadBtn.setOnAction(e -> {
            if (formBox.getValue() == null || streamBox.getValue() == null || streamBox.getValue().isBlank()) {
                showAlert("Select a form and enter a stream.");
                return;
            }
            int form = formBox.getValue();
            String stream = streamBox.getValue().trim();
            spinner.setVisible(true);
            saveBtn.setDisable(true);
            subjectCheckBoxes.clear();
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    Set<Long> assigned = new HashSet<>();
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "SELECT subject_id FROM stream_subjects WHERE form = ? AND stream = ?")) {
                        ps.setInt(1, form);
                        ps.setString(2, stream);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) assigned.add(rs.getLong("subject_id"));
                        }
                    } catch (SQLException ex) { throw new RuntimeException(ex); }

                    java.util.List<SubjectInfo> subjects = new ArrayList<>();
                    try (Connection conn = db.getConnection();
                         Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT id, subject_code, subject_name FROM subjects ORDER BY subject_name")) {
                        while (rs.next())
                            subjects.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
                    } catch (SQLException ex) { throw new RuntimeException(ex); }

                    javafx.application.Platform.runLater(() -> {
                        subjectList.getChildren().clear();
                        for (SubjectInfo si : subjects) {
                            CheckBox cb = new CheckBox(si.code + " - " + si.name);
                            cb.setSelected(assigned.contains(si.id));
                            cb.setFont(Font.font("System", 13));
                            subjectCheckBoxes.put(si.id, cb);
                            subjectList.getChildren().add(cb);
                        }
                        spinner.setVisible(false);
                        saveBtn.setDisable(false);
                        statusLabel.setText(subjects.size() + " subjects loaded");
                    });
                    return null;
                }
            };
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                showAlert("Error: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        saveBtn.setOnAction(e -> {
            int form = formBox.getValue();
            String stream = streamBox.getValue().trim();
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    try (Connection conn = db.getConnection();
                         PreparedStatement del = conn.prepareStatement(
                             "DELETE FROM stream_subjects WHERE form = ? AND stream = ?");
                         PreparedStatement ins = conn.prepareStatement(
                             "INSERT INTO stream_subjects (form, stream, subject_id) VALUES (?,?,?)")) {
                        del.setInt(1, form);
                        del.setString(2, stream);
                        del.executeUpdate();

                        for (var entry : subjectCheckBoxes.entrySet()) {
                            if (entry.getValue().isSelected()) {
                                ins.setInt(1, form);
                                ins.setString(2, stream);
                                ins.setLong(3, entry.getKey());
                                ins.executeUpdate();
                            }
                        }
                    } catch (SQLException ex) { throw new RuntimeException(ex); }
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                spinner.setVisible(false);
                statusLabel.setText("Assignments saved for Form " + form + " " + stream);
            });
            task.setOnFailed(ev -> {
                spinner.setVisible(false);
                showAlert("Error: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        view.getChildren().addAll(header, controls, spinner, subjectList, saveBtn, statusLabel);
        setView(view);
    }

    private record SubjectInfo(long id, String code, String name) {}

    private void showAlert(String msg) {
        javafx.application.Platform.runLater(() ->
            new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }
}
