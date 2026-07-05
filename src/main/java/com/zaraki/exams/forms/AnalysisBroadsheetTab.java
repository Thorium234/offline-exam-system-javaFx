package com.zaraki.exams.forms;

import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AnalysisBroadsheetTab {

    private final IExamAnalysisService service;
    private final Stage stage;
    private final TableView<StudentResultRow> table = new TableView<>();

    public AnalysisBroadsheetTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudentResultRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("classRank")); cPos.setPrefWidth(45);

        TableColumn<StudentResultRow, Number> cPrevPos = new TableColumn<>("Prev Pos");
        cPrevPos.setCellValueFactory(new PropertyValueFactory<>("prevPosition")); cPrevPos.setPrefWidth(65);

        TableColumn<StudentResultRow, String> cPosChg = new TableColumn<>("\u0394 Pos");
        cPosChg.setCellValueFactory(new PropertyValueFactory<>("positionChangeDisplay")); cPosChg.setPrefWidth(55);

        table.getColumns().addAll(
            cPos, cPrevPos, cPosChg,
            UIUtils.col("Admission", "admissionNumber", 100),
            UIUtils.col("Name", "fullName", 170),
            UIUtils.col("Stream", "stream", 70),
            UIUtils.col("Marks", "totalMarks", 70),
            UIUtils.col("Pts", "totalPoints", 50),
            UIUtils.col("Prev Pts", "prevTotalMarks", 65),
            UIUtils.col("Mean", "meanPoints", 55),
            UIUtils.col("Grade", "meanGrade", 65),
            UIUtils.col("Out Of", "classSize", 55)
        );

        table.setRowFactory(tv -> {
            TableRow<StudentResultRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    StudentResultRow r = row.getItem();
                    showStudentWeakAreas(r.getStudentId(), r.getAdmissionNumber(), r.getFullName());
                }
            });
            return row;
        });

        content.getChildren().add(table);
        return content;
    }

    public void load(long examId) {
        Task<List<IExamAnalysisService.StudentResult>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.StudentResult> call() {
                return service.computeClassRankings(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.StudentResult> results = task.getValue();
            long prevExamId = service.findPreviousExam(examId);
            Map<Long, Double> prevTotals = prevExamId > 0 ? service.getExamStudentTotals(prevExamId) : Collections.emptyMap();
            Map<Long, Integer> prevRanks = prevExamId > 0 ? service.getExamStudentRanks(prevExamId) : Collections.emptyMap();
            ObservableList<StudentResultRow> rows = FXCollections.observableArrayList();
            for (IExamAnalysisService.StudentResult r : results) {
                double prevMarks = prevTotals.getOrDefault(r.studentId(), 0.0);
                int prevPos = prevRanks.getOrDefault(r.studentId(), 0);
                int change = prevPos > 0 ? prevPos - r.classRank() : 0;
                rows.add(new StudentResultRow(r.studentId(), r.admissionNumber(), r.fullName(),
                    r.form(), r.stream(), r.totalMarks(), r.totalPoints(), r.meanPoints(),
                    r.meanGrade(), r.classRank(), r.classSize(), prevMarks, prevPos, change));
            }
            table.setItems(rows);
        });
        new Thread(task).start();
    }

    private void showStudentWeakAreas(long studentId, String admission, String name) {
        if (currentExamId == 0) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Weak Areas \u2014 " + name);
        dialog.setHeaderText(name + " (" + admission + ") \u2014 Subjects sorted by weakest score");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        ProgressIndicator spin = new ProgressIndicator();
        spin.setPrefSize(20, 20);
        content.getChildren().add(spin);
        dialog.getDialogPane().setContent(content);

        Task<List<IExamAnalysisService.StudentWeakArea>> task = new Task<>() {
            @Override protected List<IExamAnalysisService.StudentWeakArea> call() {
                return service.computeStudentWeakAreas(currentExamId, studentId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<IExamAnalysisService.StudentWeakArea> areas = task.getValue();
            content.getChildren().clear();
            TableView<IExamAnalysisService.StudentWeakArea> t = new TableView<>();
            t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            t.getColumns().addAll(
                UIUtils.col("Subject", "subjectName", 160),
                UIUtils.col("Score", "score", 70),
                UIUtils.col("Grade", "grade", 60),
                UIUtils.col("Class Avg", "classMean", 80),
                UIUtils.col("Deviation", "deviation", 80)
            );
            t.setItems(FXCollections.observableArrayList(areas));
            t.setPrefHeight(300);
            content.getChildren().add(t);
        });
        task.setOnFailed(ev -> {
            content.getChildren().clear();
            content.getChildren().add(new Label("Error: " + task.getException().getMessage()));
        });
        new Thread(task).start();
        dialog.showAndWait();
    }

    private long currentExamId;

    public void setCurrentExamId(long examId) {
        this.currentExamId = examId;
    }

    public static class StudentResultRow {
        private final long studentId;
        private final String admissionNumber, fullName, form, stream, meanGrade;
        private final double totalMarks, meanPoints, prevTotalMarks;
        private final int totalPoints, classRank, classSize, prevPosition, positionChange;

        public StudentResultRow(long studentId, String admissionNumber, String fullName,
                                String form, String stream, double totalMarks, int totalPoints,
                                double meanPoints, String meanGrade, int classRank, int classSize,
                                double prevTotalMarks, int prevPosition, int positionChange) {
            this.studentId = studentId; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.form = form; this.stream = stream; this.totalMarks = totalMarks; this.totalPoints = totalPoints;
            this.meanPoints = meanPoints; this.meanGrade = meanGrade; this.classRank = classRank;
            this.classSize = classSize; this.prevTotalMarks = prevTotalMarks; this.prevPosition = prevPosition;
            this.positionChange = positionChange;
        }
        public long getStudentId() { return studentId; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getForm() { return form; }
        public String getStream() { return stream; }
        public double getTotalMarks() { return totalMarks; }
        public int getTotalPoints() { return totalPoints; }
        public double getMeanPoints() { return meanPoints; }
        public String getMeanGrade() { return meanGrade; }
        public int getClassRank() { return classRank; }
        public int getClassSize() { return classSize; }
        public double getPrevTotalMarks() { return prevTotalMarks; }
        public int getPrevPosition() { return prevPosition; }
        public int getPositionChange() { return positionChange; }
        public String getPositionChangeDisplay() {
            if (positionChange > 0) return "+" + positionChange;
            if (positionChange < 0) return String.valueOf(positionChange);
            return "0";
        }
    }
}
