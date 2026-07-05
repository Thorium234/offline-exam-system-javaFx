package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.IExamRepository;
import com.zaraki.exams.repository.IUserRepository;
import com.zaraki.exams.repository.ExamRepositoryImpl;
import com.zaraki.exams.repository.UserRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.sql.*;

public class PublishForm {

    private final DatabaseEngine db;
    private final IExamRepository examRepo;
    private final IUserRepository userRepo;
    private final String displayName;
    private final String username;
    private final String role;
    private final long currentUserId;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final VBox mainArea = new VBox(10);
    private final Label statusLabel = new Label();
    private long currentExamId;

    private PublishAdminPanel adminPanel;
    private PublishTeacherPanel teacherPanel;

    public PublishForm(DatabaseEngine db, String displayName, String username, String role) {
        this.db = db;
        this.examRepo = new ExamRepositoryImpl();
        this.userRepo = new UserRepositoryImpl();
        this.displayName = displayName;
        this.username = username;
        this.role = role;
        this.currentUserId = userRepo.resolveUserId(username);
        if ("admin".equals(role)) {
            this.adminPanel = new PublishAdminPanel(db, username);
        }
        this.teacherPanel = new PublishTeacherPanel(db, username);
    }

    public VBox getView() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("Publish Management");
        Label info = new Label("Upload marks per subject, publish each subject, then release the exam for report generation.");

        HBox examRow = new HBox(10);
        examRow.getChildren().addAll(new Label("Exam:"), examBox);
        Button loadBtn = new Button("Load");
        loadBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white;");
        examRow.getChildren().add(loadBtn);

        mainArea.setVisible(false);
        root.getChildren().addAll(header, info, examRow, statusLabel, mainArea);

        loadExams();
        loadBtn.setOnAction(e -> loadExam());
        return root;
    }

    private void loadExams() {
        try {
            var exams = examRepo.findAllDesc();
            for (var e : exams)
                examBox.getItems().add(e.get("id") + " - " + e.get("academic_year")
                    + " " + e.get("term") + " " + e.get("exam_series"));
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void loadExam() {
        if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
        currentExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
        mainArea.getChildren().clear();
        mainArea.setVisible(true);

        if ("admin".equals(role)) {
            adminPanel.load(currentExamId, () -> {});
            adminPanel.setUploadCallback(() -> {
                String selected = examBox.getValue();
                if (selected != null) {
                    long eid = Long.parseLong(selected.split(" - ")[0]);
                    String subj = "Subject";
                }
            });
            mainArea.getChildren().add(adminPanel.getView());
        } else {
            showTeacherView();
        }

        String released = isExamReleased(currentExamId) ? " (RELEASED)" : "";
        statusLabel.setText("Exam: " + examBox.getValue() + released);
    }

    private void showTeacherView() {
        mainArea.getChildren().clear();
        VBox teacherView = new VBox(10);

        // Ensure exam_subjects rows exist
        String sql = "INSERT OR IGNORE INTO exam_subjects (exam_id, subject_id, out_of) SELECT ?, id, 100 FROM subjects";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, currentExamId); ps.executeUpdate();
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); return; }

        TableView<TeacherSubjectRow> tbl = new TableView<>();
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        var colSubj = UIUtils.<TeacherSubjectRow>col("Subject", "subjectName", 250);
        var colStatus = UIUtils.<TeacherSubjectRow>col("Status", "status", 150);
        tbl.getColumns().addAll(colSubj, colStatus);

        // Load teacher subjects
        ObservableList<TeacherSubjectRow> subjects = FXCollections.observableArrayList();
        String subjSql = "SELECT s.id, s.subject_name, " +
            "(SELECT COUNT(*) FROM marks m WHERE m.exam_id = ? AND m.subject_id = s.id) AS mc, " +
            "(SELECT published FROM exam_subjects WHERE exam_id = ? AND subject_id = s.id) AS pub " +
            "FROM subjects s " +
            "JOIN teacher_subjects ts ON ts.subject_id = s.id AND ts.user_id = ? " +
            "ORDER BY s.subject_name";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(subjSql)) {
            ps.setLong(1, currentExamId); ps.setLong(2, currentExamId); ps.setLong(3, currentUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                boolean isPub = rs.getInt("pub") == 1;
                String st = isPub ? "Published" : (rs.getInt("mc") > 0 ? "Uploaded" : "Not Uploaded");
                subjects.add(new TeacherSubjectRow(rs.getLong("id"), rs.getString("subject_name"), st, isPub));
            }
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }

        tbl.setItems(subjects);
        tbl.setPrefHeight(250);

        tbl.setRowFactory(tv -> {
            TableRow<TeacherSubjectRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    TeacherSubjectRow r = row.getItem();
                    if (r.published) { UIUtils.showError("Already published."); return; }
                    int outOf = 100;
                    try (PreparedStatement ps = db.getConnection().prepareStatement(
                            "SELECT out_of FROM exam_subjects WHERE exam_id = ? AND subject_id = ?")) {
                        ps.setLong(1, currentExamId); ps.setLong(2, r.subjectId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) outOf = rs.getInt("out_of");
                    } catch (SQLException ex) {}
                    teacherPanel.showUploadView(currentExamId, r.subjectId, r.subjectName, outOf, () -> showTeacherView());
                    mainArea.getChildren().setAll(teacherPanel.getView());
                }
            });
            return row;
        });

        teacherView.getChildren().add(new Label("Double-click a subject to upload marks:"));
        teacherView.getChildren().add(tbl);
        mainArea.getChildren().add(teacherView);
    }

    private record TeacherSubjectRow(long subjectId, String subjectName, String status, boolean published) {}

    public static boolean isExamReleased(long examId) {
        return new ExamRepositoryImpl().isReleased(examId);
    }
}
