package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;

import java.sql.*;

public class PublishScoreTable {

    private final DatabaseEngine db;
    private final TableView<MarkRow> table = new TableView<>();
    private final ObservableList<MarkRow> data = FXCollections.observableArrayList();
    private long currentExamId;
    private long currentSubjectId;
    private int currentOutOf = 100;

    public PublishScoreTable(DatabaseEngine db) {
        this.db = db;
        buildTable();
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(true);
        table.setPrefHeight(350);
        table.setItems(data);

        TableColumn<MarkRow, Integer> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().pos).asObject());
        cPos.setPrefWidth(40);

        TableColumn<MarkRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().admission));
        cAdm.setPrefWidth(120);

        TableColumn<MarkRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        cName.setPrefWidth(200);

        TableColumn<MarkRow, Double> cScore = new TableColumn<>("Score");
        cScore.setCellValueFactory(d -> {
            Double s = d.getValue().score;
            return new javafx.beans.property.SimpleDoubleProperty(s != null ? s : 0).asObject();
        });
        cScore.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        cScore.setOnEditCommit(e -> {
            MarkRow mr = e.getRowValue();
            Double v = e.getNewValue();
            if (v != null && Double.isFinite(v) && v >= 0 && v <= currentOutOf) {
                mr.score = v;
                mr.dirty = true;
                table.refresh();
            } else if (v != null && !Double.isFinite(v)) {
                com.zaraki.exams.util.UIUtils.showError("Invalid score value.");
            } else if (v != null) {
                com.zaraki.exams.util.UIUtils.showError("Score must be between 0 and " + currentOutOf + ".");
            }
        });
        cScore.setPrefWidth(100);

        TableColumn<MarkRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().grade));
        cGrade.setPrefWidth(60);

        TableColumn<MarkRow, Integer> cPts = new TableColumn<>("Points");
        cPts.setCellValueFactory(d -> {
            Integer p = d.getValue().points;
            return new javafx.beans.property.SimpleIntegerProperty(p != null ? p : 0).asObject();
        });
        cPts.setPrefWidth(60);

        table.getColumns().addAll(cPos, cAdm, cName, cScore, cGrade, cPts);
    }

    public TableView<MarkRow> getTable() { return table; }
    public ObservableList<MarkRow> getData() { return data; }

    public void loadStudents(long examId, long subjectId, int outOf) {
        this.currentExamId = examId;
        this.currentSubjectId = subjectId;
        this.currentOutOf = outOf;
        data.clear();
        String sql = """
            SELECT s.id, s.admission_number, s.full_name, m.score, m.grade_achieved, m.points_achieved
            FROM students s
            LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? AND m.subject_id = ?
            WHERE s.deallocated = 0
            ORDER BY s.admission_number
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, subjectId);
            ResultSet rs = ps.executeQuery();
            int pos = 0;
            while (rs.next()) {
                pos++;
                Double score = rs.getObject("score") != null ? rs.getDouble("score") : null;
                data.add(new MarkRow(pos, rs.getLong("id"), rs.getString("admission_number"),
                    rs.getString("full_name"), score, rs.getString("grade_achieved"),
                    rs.getObject("points_achieved") != null ? rs.getInt("points_achieved") : null, false));
            }
        } catch (SQLException e) {
            com.zaraki.exams.util.UIUtils.showError(e.getMessage());
        }
    }

    public int getCurrentOutOf() { return currentOutOf; }
    public long getCurrentSubjectId() { return currentSubjectId; }
    public void setDirty(int index) { data.get(index).dirty = true; }

    public static class MarkRow {
        public final int pos;
        public final long studentId;
        public final String admission;
        public final String name;
        public Double score;
        public String grade;
        public Integer points;
        public boolean dirty;

        public MarkRow(int pos, long studentId, String admission, String name,
                       Double score, String grade, Integer points, boolean dirty) {
            this.pos = pos; this.studentId = studentId; this.admission = admission;
            this.name = name; this.score = score; this.grade = grade;
            this.points = points; this.dirty = dirty;
        }
    }
}
