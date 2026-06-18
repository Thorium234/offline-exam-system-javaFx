package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.List;

public class AnalysisForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;

    public AnalysisForm(DatabaseEngine db) {
        this.db = db;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Exam Analysis");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox controls = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(300);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }

        Button autoGradeBtn = new Button("Auto-Grade All");
        Button rankBtn = new Button("Compute Rankings");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        controls.getChildren().addAll(examBox, autoGradeBtn, rankBtn, spinner);

        TabPane tabs = new TabPane();

        Tab classTab = new Tab("Class Rankings");
        VBox classContent = new VBox(10);
        classContent.setPadding(new Insets(10));
        TableView<StudentResultRow> classTable = new TableView<>();
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<StudentResultRow, Integer> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("classRank")); cPos.setPrefWidth(40);
        TableColumn<StudentResultRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdm.setPrefWidth(110);
        TableColumn<StudentResultRow, String> cFull = new TableColumn<>("Name");
        cFull.setCellValueFactory(new PropertyValueFactory<>("fullName")); cFull.setPrefWidth(180);
        TableColumn<StudentResultRow, String> cStream = new TableColumn<>("Stream");
        cStream.setCellValueFactory(new PropertyValueFactory<>("stream")); cStream.setPrefWidth(80);
        TableColumn<StudentResultRow, Number> cTotalMarks = new TableColumn<>("Marks");
        cTotalMarks.setCellValueFactory(new PropertyValueFactory<>("totalMarks")); cTotalMarks.setPrefWidth(70);
        TableColumn<StudentResultRow, Number> cTotalPts = new TableColumn<>("Pts");
        cTotalPts.setCellValueFactory(new PropertyValueFactory<>("totalPoints")); cTotalPts.setPrefWidth(50);
        TableColumn<StudentResultRow, Number> cMeanPts = new TableColumn<>("Mean");
        cMeanPts.setCellValueFactory(new PropertyValueFactory<>("meanPoints")); cMeanPts.setPrefWidth(60);
        TableColumn<StudentResultRow, String> cMeanGrade = new TableColumn<>("Grade");
        cMeanGrade.setCellValueFactory(new PropertyValueFactory<>("meanGrade")); cMeanGrade.setPrefWidth(70);
        TableColumn<StudentResultRow, Number> cClassSize = new TableColumn<>("Out Of");
        cClassSize.setCellValueFactory(new PropertyValueFactory<>("classSize")); cClassSize.setPrefWidth(60);
        classTable.getColumns().addAll(cPos, cAdm, cFull, cStream, cTotalMarks, cTotalPts, cMeanPts, cMeanGrade);
        classContent.getChildren().add(classTable);
        classTab.setContent(classContent);

        Tab subjectTab = new Tab("Subject Metrics");
        VBox subjectContent = new VBox(10);
        subjectContent.setPadding(new Insets(10));
        TableView<SubjectMetricRow> subjectTable = new TableView<>();
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<SubjectMetricRow, Number> cSubRank = new TableColumn<>("Rank");
        cSubRank.setCellValueFactory(new PropertyValueFactory<>("rank")); cSubRank.setPrefWidth(50);
        TableColumn<SubjectMetricRow, String> cSubName = new TableColumn<>("Subject");
        cSubName.setCellValueFactory(new PropertyValueFactory<>("subjectName")); cSubName.setPrefWidth(200);
        TableColumn<SubjectMetricRow, String> cDept = new TableColumn<>("Dept");
        cDept.setCellValueFactory(new PropertyValueFactory<>("department")); cDept.setPrefWidth(120);
        TableColumn<SubjectMetricRow, Number> cMeanScore = new TableColumn<>("Mean Score");
        cMeanScore.setCellValueFactory(new PropertyValueFactory<>("meanScore")); cMeanScore.setPrefWidth(100);
        TableColumn<SubjectMetricRow, String> cMeanGradeCol = new TableColumn<>("Mean Grade");
        cMeanGradeCol.setCellValueFactory(new PropertyValueFactory<>("meanGrade")); cMeanGradeCol.setPrefWidth(90);
        TableColumn<SubjectMetricRow, Number> cStdDev = new TableColumn<>("Std Dev");
        cStdDev.setCellValueFactory(new PropertyValueFactory<>("stdDev")); cStdDev.setPrefWidth(90);
        TableColumn<SubjectMetricRow, Number> cCandidates = new TableColumn<>("Candidates");
        cCandidates.setCellValueFactory(new PropertyValueFactory<>("candidates")); cCandidates.setPrefWidth(90);
        subjectTable.getColumns().addAll(cSubRank, cSubName, cDept, cMeanScore, cMeanGradeCol, cStdDev, cCandidates);
        subjectContent.getChildren().add(subjectTable);
        subjectTab.setContent(subjectContent);

        tabs.getTabs().addAll(classTab, subjectTab);
        view.getChildren().addAll(header, controls, tabs);

        rankBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            spinner.setVisible(true);

            Task<List<ExamAnalysisService.StudentResult>> task = new Task<>() {
                @Override protected List<ExamAnalysisService.StudentResult> call() {
                    return analysisService.computeClassRankings(examId);
                }
            };
            task.setOnSucceeded(ev -> {
                List<ExamAnalysisService.StudentResult> results = task.getValue();
                ObservableList<StudentResultRow> rows = FXCollections.observableArrayList();
                for (ExamAnalysisService.StudentResult r : results)
                    rows.add(new StudentResultRow(r.studentId(), r.admissionNumber(), r.fullName(),
                        r.form(), r.stream(), r.totalMarks(), r.totalPoints(), r.meanPoints(),
                        r.meanGrade(), r.classRank(), r.classSize()));
                classTable.setItems(rows);
                spinner.setVisible(false);
            });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();

            Task<List<ExamAnalysisService.SubjectMetrics>> subTask = new Task<>() {
                @Override protected List<ExamAnalysisService.SubjectMetrics> call() {
                    return analysisService.computeSubjectMetrics(examId);
                }
            };
            subTask.setOnSucceeded(ev -> {
                List<ExamAnalysisService.SubjectMetrics> results = subTask.getValue();
                ObservableList<SubjectMetricRow> rows = FXCollections.observableArrayList();
                for (ExamAnalysisService.SubjectMetrics r : results)
                    rows.add(new SubjectMetricRow(r.subjectId(), r.subjectName(), r.department(),
                        r.meanScore(), r.meanGrade(), r.stdDev(), r.subjectRank(), r.totalCandidates()));
                subjectTable.setItems(rows);
            });
            subTask.setOnFailed(ev -> showAlert(subTask.getException().getMessage()));
            new Thread(subTask).start();
        });

        autoGradeBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    analysisService.autoGradeExam(examId);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { showInfo("Auto-grading complete."); spinner.setVisible(false); });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        return view;
    }

    private void showAlert(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait()); }
    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait()); }

    public static class StudentResultRow {
        private final long studentId; private final String admissionNumber, fullName, form, stream;
        private final double totalMarks, meanPoints; private final int totalPoints, classRank, classSize;
        private final String meanGrade;
        public StudentResultRow(long studentId, String admissionNumber, String fullName,
                                String form, String stream, double totalMarks, int totalPoints,
                                double meanPoints, String meanGrade, int classRank, int classSize) {
            this.studentId = studentId; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.form = form; this.stream = stream; this.totalMarks = totalMarks; this.totalPoints = totalPoints;
            this.meanPoints = meanPoints; this.meanGrade = meanGrade; this.classRank = classRank; this.classSize = classSize;
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
    }

    public static class SubjectMetricRow {
        private final long subjectId; private final String subjectName, department;
        private final double meanScore, stdDev; private final String meanGrade;
        private final int rank, candidates;
        public SubjectMetricRow(long subjectId, String subjectName, String department,
                                double meanScore, String meanGrade, double stdDev,
                                int rank, int candidates) {
            this.subjectId = subjectId; this.subjectName = subjectName; this.department = department;
            this.meanScore = meanScore; this.meanGrade = meanGrade; this.stdDev = stdDev;
            this.rank = rank; this.candidates = candidates;
        }
        public long getSubjectId() { return subjectId; }
        public String getSubjectName() { return subjectName; }
        public String getDepartment() { return department; }
        public double getMeanScore() { return meanScore; }
        public String getMeanGrade() { return meanGrade; }
        public double getStdDev() { return stdDev; }
        public int getRank() { return rank; }
        public int getCandidates() { return candidates; }
    }
}
