package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.UIUtils;
import static com.zaraki.exams.forms.AppTheme.PRIMARY;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SubjectAssignmentForm {



    private final DatabaseEngine db;
    private final StackPane root;
    private final Map<Long, CheckBox> subjectCheckBoxes = new LinkedHashMap<>();
    private final ComboBox<Integer> formBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private Button saveBtn;
    private Label statusLabel;
    private ProgressIndicator spinner;
    private VBox subjectList;

    public SubjectAssignmentForm(DatabaseEngine db) {
        this.db = db;
        this.root = new StackPane();
        showAssignment();
    }

    public Node getView() { return root; }
    private void setView(Node node) { root.getChildren().setAll(node); }

    public void loadForStream(int form, String stream) {
        formBox.setValue(form);
        streamBox.setValue(stream);
        loadSubjects();
    }

    private void showAssignment() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("Stream Subject Assignment");

        HBox controls = new HBox(15);
        formBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        formBox.setPromptText("Select Form");
        formBox.setPrefWidth(150);

        streamBox.setEditable(true);
        streamBox.setPromptText("Stream (e.g. A)");
        streamBox.setPrefWidth(150);
        loadStreams(null);

        formBox.setOnAction(e -> {
            Integer f = formBox.getValue();
            loadStreams(f);
        });

        Button loadBtn = new Button("Load Subjects");
        loadBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white;");

        controls.getChildren().addAll(new Label("Form:"), formBox, new Label("Stream:"), streamBox, loadBtn);

        subjectList = new VBox(5);
        subjectList.setPadding(new Insets(10));
        subjectList.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8;");

        saveBtn = new Button("Save Assignments");
        saveBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setDisable(true);

        statusLabel = new Label();

        spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        loadBtn.setOnAction(e -> loadSubjects());

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
                             "INSERT INTO stream_subjects (form, stream, subject_id) VALUES (?,?,?)");
                         PreparedStatement delStudSubj = conn.prepareStatement(
                             "DELETE FROM student_subjects WHERE student_id IN (SELECT id FROM students WHERE form = ? AND stream = ? AND deallocated = 0)");
                         PreparedStatement insStudSubj = conn.prepareStatement(
                             "INSERT OR IGNORE INTO student_subjects (student_id, subject_id) SELECT s.id, ? FROM students s WHERE s.form = ? AND s.stream = ? AND s.deallocated = 0")) {
                        del.setInt(1, form);
                        del.setString(2, stream);
                        del.executeUpdate();

                        delStudSubj.setInt(1, form);
                        delStudSubj.setString(2, stream);
                        delStudSubj.executeUpdate();

                        for (var entry : subjectCheckBoxes.entrySet()) {
                            if (entry.getValue().isSelected()) {
                                ins.setInt(1, form);
                                ins.setString(2, stream);
                                ins.setLong(3, entry.getKey());
                                ins.executeUpdate();

                                insStudSubj.setLong(1, entry.getKey());
                                insStudSubj.setInt(2, form);
                                insStudSubj.setString(3, stream);
                                insStudSubj.executeUpdate();
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
                UIUtils.showError("Error: " + task.getException().getMessage());
            });
            new Thread(task).start();
        });

        view.getChildren().addAll(header, controls, spinner, subjectList, saveBtn, statusLabel);
        setView(view);
    }

    private void loadStreams(Integer form) {
        streamBox.getItems().clear();
        String sql = form == null
            ? "SELECT DISTINCT stream FROM streams ORDER BY stream"
            : "SELECT stream FROM streams WHERE form = ? ORDER BY stream";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (form != null) ps.setInt(1, form);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) streamBox.getItems().add(rs.getString("stream"));
        } catch (SQLException e) { /* ignore */ }
    }

    private void loadSubjects() {
        if (formBox.getValue() == null || streamBox.getValue() == null || streamBox.getValue().isBlank()) {
            UIUtils.showError("Select a form and enter a stream.");
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
            UIUtils.showError("Error: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    private record SubjectInfo(long id, String code, String name) {}


}
