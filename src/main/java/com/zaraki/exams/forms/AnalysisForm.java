package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.forms.PublishForm;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.zaraki.exams.reporting.ReportCardGenerator;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.sql.*;
import java.util.*;

public class AnalysisForm {

    private final DatabaseEngine db;
    private final ExamAnalysisService analysisService;
    private final Stage stage;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final ProgressIndicator spinner = new ProgressIndicator();

    public AnalysisForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.analysisService = new ExamAnalysisService();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Exam Analysis");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        loadExams(examBox);

        Button autoGradeBtn = new Button("Auto-Grade All");
        Button rankBtn = new Button("Compute Rankings");
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox controls = new HBox(10);
        controls.getChildren().addAll(examBox, autoGradeBtn, rankBtn, spinner);

        TabPane tabs = new TabPane();

        Tab classTab = buildClassRankingsTab();
        Tab subjectTab = buildSubjectMetricsTab();
        Tab gradeDistTab = buildGradeDistributionTab();
        Tab comparisonTab = buildExamComparisonTab();
        Tab meritTab = buildMeritListTab();

        tabs.getTabs().addAll(classTab, subjectTab, gradeDistTab, comparisonTab, meritTab);
        view.getChildren().addAll(header, controls, tabs);

        rankBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!PublishForm.isExamReleased(examId)) { showAlert("Exam not released by admin. Analysis unavailable."); return; }
            spinner.setVisible(true);
            computeAllTabs(examId);
        });

        autoGradeBtn.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
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

    // ────────────────────── Class Rankings Tab ──────────────────────

    private final TableView<StudentResultRow> classTable = new TableView<>();

    private Tab buildClassRankingsTab() {
        Tab tab = new Tab("Broadsheet");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudentResultRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("classRank")); cPos.setPrefWidth(45);

        TableColumn<StudentResultRow, Number> cPrevPos = new TableColumn<>("Prev Pos");
        cPrevPos.setCellValueFactory(new PropertyValueFactory<>("prevPosition")); cPrevPos.setPrefWidth(65);

        TableColumn<StudentResultRow, String> cPosChg = new TableColumn<>("Δ Pos");
        cPosChg.setCellValueFactory(new PropertyValueFactory<>("positionChangeDisplay")); cPosChg.setPrefWidth(55);

        classTable.getColumns().addAll(
            cPos, cPrevPos, cPosChg,
            col("Admission", "admissionNumber", 100),
            col("Name", "fullName", 170),
            col("Stream", "stream", 70),
            col("Marks", "totalMarks", 70),
            col("Pts", "totalPoints", 50),
            col("Prev Pts", "prevTotalMarks", 65),
            col("Mean", "meanPoints", 55),
            col("Grade", "meanGrade", 65),
            col("Out Of", "classSize", 55)
        );

        content.getChildren().add(classTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Subject Metrics Tab ──────────────────────

    private final TableView<SubjectMetricRow> subjectTable = new TableView<>();

    private Tab buildSubjectMetricsTab() {
        Tab tab = new Tab("Subject Metrics");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.getColumns().addAll(
            col("Rank", "rank", 50),
            col("Subject", "subjectName", 200),
            col("Dept", "department", 120),
            col("Mean Score", "meanScore", 100),
            col("Mean Grade", "meanGrade", 90),
            col("Std Dev", "stdDev", 90),
            col("Candidates", "candidates", 90)
        );
        content.getChildren().add(subjectTable);
        tab.setContent(content);
        return tab;
    }

    // ────────────────────── Grade Distribution Tab ──────────────────────

    private final TableView<GradeDistRow> gradeDistTable = new TableView<>();

    private Tab buildGradeDistributionTab() {
        Tab tab = new Tab("Grade Distribution");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        gradeDistTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        content.getChildren().add(gradeDistTable);
        tab.setContent(content);
        return tab;
    }

    private void populateGradeDistribution(long examId) {
        gradeDistTable.getColumns().clear();
        Task<List<ExamAnalysisService.GradeDistribution>> task = new Task<>() {
            @Override protected List<ExamAnalysisService.GradeDistribution> call() {
                return analysisService.computeGradeDistribution(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<ExamAnalysisService.GradeDistribution> data = task.getValue();

            Set<String> allGrades = new LinkedHashSet<>();
            for (var gd : data) allGrades.addAll(gd.gradeCounts().keySet());
            List<String> gradeOrder = new ArrayList<>(allGrades);

            TableColumn<GradeDistRow, String> cSubject = new TableColumn<>("Subject");
            cSubject.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
            cSubject.setPrefWidth(200);
            gradeDistTable.getColumns().add(cSubject);

            Map<String, TableColumn<GradeDistRow, Number>> gradeCols = new LinkedHashMap<>();
            for (String g : gradeOrder) {
                TableColumn<GradeDistRow, Number> col = new TableColumn<>(g);
                col.setPrefWidth(50);
                final String grade = g;
                col.setCellValueFactory(cd -> {
                    GradeDistRow row = cd.getValue();
                    return new SimpleObjectProperty<>(row.gradeCounts.getOrDefault(grade, 0));
                });
                gradeDistTable.getColumns().add(col);
                gradeCols.put(g, col);
            }

            ObservableList<GradeDistRow> rows = FXCollections.observableArrayList();
            for (var gd : data) rows.add(new GradeDistRow(gd.subjectName(), gd.gradeCounts()));
            gradeDistTable.setItems(rows);
        });
        task.setOnFailed(ev -> showAlert(task.getException().getMessage()));
        new Thread(task).start();
    }

    public static class GradeDistRow {
        private final String subjectName;
        private final Map<String, Integer> gradeCounts;
        public GradeDistRow(String subjectName, Map<String, Integer> gradeCounts) {
            this.subjectName = subjectName;
            this.gradeCounts = gradeCounts;
        }
        public String getSubjectName() { return subjectName; }
    }

    // ────────────────────── Exam Comparison Tab ──────────────────────

    private final ComboBox<String> exam1Box = new ComboBox<>();
    private final ComboBox<String> exam2Box = new ComboBox<>();
    private final TableView<ExamComparisonRow> compareTable = new TableView<>();

    private Tab buildExamComparisonTab() {
        Tab tab = new Tab("Most Improved / Dropped");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        loadExams(exam1Box);
        loadExams(exam2Box);
        exam1Box.setPromptText("Earlier Exam");
        exam2Box.setPromptText("Later Exam");

        Button compareBtn = new Button("Compare");
        HBox controls = new HBox(10, new Label("From:"), exam1Box, new Label("To:"), exam2Box, compareBtn);

        compareTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ExamComparisonRow, Number> cPosC = new TableColumn<>("#");
        cPosC.setCellValueFactory(new PropertyValueFactory<>("rank")); cPosC.setPrefWidth(50);
        TableColumn<ExamComparisonRow, String> cAdmC = new TableColumn<>("Admission");
        cAdmC.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdmC.setPrefWidth(100);
        TableColumn<ExamComparisonRow, String> cNameC = new TableColumn<>("Name");
        cNameC.setCellValueFactory(new PropertyValueFactory<>("fullName")); cNameC.setPrefWidth(160);
        TableColumn<ExamComparisonRow, String> cFormC = new TableColumn<>("Form");
        cFormC.setCellValueFactory(new PropertyValueFactory<>("form")); cFormC.setPrefWidth(55);
        TableColumn<ExamComparisonRow, String> cStreamC = new TableColumn<>("Stream");
        cStreamC.setCellValueFactory(new PropertyValueFactory<>("stream")); cStreamC.setPrefWidth(70);
        TableColumn<ExamComparisonRow, Number> cE1 = new TableColumn<>("Exam1 Pts");
        cE1.setCellValueFactory(new PropertyValueFactory<>("exam1Total")); cE1.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE1Pos = new TableColumn<>("Pos1");
        cE1Pos.setCellValueFactory(new PropertyValueFactory<>("exam1Pos")); cE1Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cE2 = new TableColumn<>("Exam2 Pts");
        cE2.setCellValueFactory(new PropertyValueFactory<>("exam2Total")); cE2.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE2Pos = new TableColumn<>("Pos2");
        cE2Pos.setCellValueFactory(new PropertyValueFactory<>("exam2Pos")); cE2Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cDiff = new TableColumn<>("Δ Pts");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difference")); cDiff.setPrefWidth(70);
        TableColumn<ExamComparisonRow, String> cPosDelta = new TableColumn<>("Δ Pos");
        cPosDelta.setCellValueFactory(new PropertyValueFactory<>("posChangeDisplay")); cPosDelta.setPrefWidth(60);
        compareTable.getColumns().addAll(cPosC, cAdmC, cNameC, cFormC, cStreamC, cE1, cE1Pos, cE2, cE2Pos, cDiff, cPosDelta);

        compareBtn.setOnAction(e -> {
            if (exam1Box.getValue() == null || exam2Box.getValue() == null) return;
            long e1 = Long.parseLong(exam1Box.getValue().split(" - ")[0]);
            long e2 = Long.parseLong(exam2Box.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<List<ExamAnalysisService.ExamComparison>> task = new Task<>() {
                @Override protected List<ExamAnalysisService.ExamComparison> call() {
                    return analysisService.compareExams(e1, e2);
                }
            };
            task.setOnSucceeded(ev -> {
                List<ExamAnalysisService.ExamComparison> list = task.getValue();
                ObservableList<ExamComparisonRow> rows = FXCollections.observableArrayList();
                int rank = 0;
                double prevDiff = Double.MAX_VALUE;
                for (int i = 0; i < list.size(); i++) {
                    ExamAnalysisService.ExamComparison ec = list.get(i);
                    if (ec.difference() < prevDiff) rank = i + 1;
                    prevDiff = ec.difference();
                    rows.add(new ExamComparisonRow(rank, ec.admissionNumber(), ec.fullName(),
                        ec.form(), ec.stream(),
                        ec.exam1Total(), ec.exam2Total(), ec.difference(),
                        ec.exam1Pos(), ec.exam2Pos(), ec.posChange()));
                }
                compareTable.setItems(rows);
                spinner.setVisible(false);
            });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        content.getChildren().addAll(controls, compareTable);
        tab.setContent(content);
        return tab;
    }

    public static class ExamComparisonRow {
        private final int rank, exam1Pos, exam2Pos;
        private final String admissionNumber, fullName, form, stream;
        private final double exam1Total, exam2Total, difference;
        public ExamComparisonRow(int rank, String admissionNumber, String fullName,
                                 String form, String stream,
                                 double exam1Total, double exam2Total, double difference,
                                 int exam1Pos, int exam2Pos, int posChange) {
            this.rank = rank; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.form = form; this.stream = stream;
            this.exam1Total = exam1Total; this.exam2Total = exam2Total; this.difference = difference;
            this.exam1Pos = exam1Pos; this.exam2Pos = exam2Pos;
        }
        public int getRank() { return rank; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getForm() { return form; }
        public String getStream() { return stream; }
        public double getExam1Total() { return exam1Total; }
        public double getExam2Total() { return exam2Total; }
        public double getDifference() { return difference; }
        public int getExam1Pos() { return exam1Pos; }
        public int getExam2Pos() { return exam2Pos; }
        public String getPosChangeDisplay() {
            int ch = exam1Pos > 0 ? exam1Pos - exam2Pos : 0;
            if (ch > 0) return "+" + ch;
            if (ch < 0) return String.valueOf(ch);
            return "0";
        }
    }

    // ────────────────────── Compute All Tabs ──────────────────────

    private void computeAllTabs(long examId) {
        Task<List<ExamAnalysisService.StudentResult>> mainTask = new Task<>() {
            @Override protected List<ExamAnalysisService.StudentResult> call() {
                return analysisService.computeClassRankings(examId);
            }
        };
        mainTask.setOnSucceeded(ev -> {
            List<ExamAnalysisService.StudentResult> results = mainTask.getValue();
            long prevExamId = analysisService.findPreviousExam(examId);
            Map<Long, Double> prevTotals = prevExamId > 0 ? analysisService.getExamStudentTotals(prevExamId) : Collections.emptyMap();
            Map<Long, Integer> prevRanks = prevExamId > 0 ? analysisService.getExamStudentRanks(prevExamId) : Collections.emptyMap();

            ObservableList<StudentResultRow> rows = FXCollections.observableArrayList();
            for (ExamAnalysisService.StudentResult r : results) {
                double prevMarks = prevTotals.getOrDefault(r.studentId(), 0.0);
                int prevPos = prevRanks.getOrDefault(r.studentId(), 0);
                int change = prevPos > 0 ? prevPos - r.classRank() : 0;
                rows.add(new StudentResultRow(r.studentId(), r.admissionNumber(), r.fullName(),
                    r.form(), r.stream(), r.totalMarks(), r.totalPoints(), r.meanPoints(),
                    r.meanGrade(), r.classRank(), r.classSize(), prevMarks, prevPos, change));
            }
            classTable.setItems(rows);

            Task<List<ExamAnalysisService.SubjectMetrics>> subTask = new Task<>() {
                @Override protected List<ExamAnalysisService.SubjectMetrics> call() {
                    return analysisService.computeSubjectMetrics(examId);
                }
            };
            subTask.setOnSucceeded(ev2 -> {
                List<ExamAnalysisService.SubjectMetrics> mResults = subTask.getValue();
                ObservableList<SubjectMetricRow> sRows = FXCollections.observableArrayList();
                for (ExamAnalysisService.SubjectMetrics r : mResults)
                    sRows.add(new SubjectMetricRow(r.subjectId(), r.subjectName(), r.department(),
                        r.meanScore(), r.meanGrade(), r.stdDev(), r.subjectRank(), r.totalCandidates()));
                subjectTable.setItems(sRows);
                spinner.setVisible(false);
            });
            subTask.setOnFailed(ev2 -> { showAlert(subTask.getException().getMessage()); spinner.setVisible(false); });
            new Thread(subTask).start();

            populateGradeDistribution(examId);
        });
        mainTask.setOnFailed(ev -> { showAlert(mainTask.getException().getMessage()); spinner.setVisible(false); });
        new Thread(mainTask).start();
    }

    // ────────────────────── Merit List Tab ──────────────────────

    private final ComboBox<String> meritExamBox = new ComboBox<>();
    private final ComboBox<String> meritGroupBox = new ComboBox<>();
    private final ToggleGroup meritGroupType = new ToggleGroup();
    private final RadioButton meritStreamRb = new RadioButton("Stream");
    private final RadioButton meritFormRb = new RadioButton("Form");
    private final TableView<MeritListRow> meritTable = new TableView<>();

    private Tab buildMeritListTab() {
        Tab tab = new Tab("Merit List");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        loadExams(meritExamBox);
        HBox examRow = new HBox(10, new Label("Exam:"), meritExamBox);
        meritExamBox.setPrefWidth(300);

        HBox typeRow = new HBox(10);
        meritStreamRb.setToggleGroup(meritGroupType);
        meritFormRb.setToggleGroup(meritGroupType);
        meritStreamRb.setSelected(true);
        meritGroupBox.setPrefWidth(200);
        loadStreams(meritGroupBox);
        meritGroupType.selectedToggleProperty().addListener((obs, old, cur) -> {
            meritGroupBox.getItems().clear();
            if (cur == meritStreamRb) loadStreams(meritGroupBox);
            else loadForms(meritGroupBox);
        });
        typeRow.getChildren().addAll(new Label("Group By:"), meritStreamRb, meritFormRb, meritGroupBox);

        HBox btnRow = new HBox(10);
        Button showBtn = new Button("Show Merit List");
        Button exportPdfBtn = new Button("Export PDF");
        ProgressIndicator mSpinner = new ProgressIndicator();
        mSpinner.setVisible(false);
        mSpinner.setPrefSize(24, 24);
        btnRow.getChildren().addAll(showBtn, exportPdfBtn, mSpinner);

        meritTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        showBtn.setOnAction(e -> {
            if (meritExamBox.getValue() == null) { showAlert("Select exam."); return; }
            if (meritGroupBox.getValue() == null) { showAlert("Select group."); return; }
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            String groupValue = meritGroupBox.getValue();
            mSpinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                loadMeritTable(examId, groupBy, groupValue);
                mSpinner.setVisible(false);
            });
            task.setOnFailed(ev -> { showAlert(task.getException().getMessage()); mSpinner.setVisible(false); });
            new Thread(task).start();
        });

        exportPdfBtn.setOnAction(e -> {
            if (meritExamBox.getValue() == null || meritGroupBox.getValue() == null) return;
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            String groupValue = meritGroupBox.getValue();

            FileChooser fc = new FileChooser();
            fc.setTitle("Export Merit List");
            fc.setInitialFileName("merit_list_" + groupBy + "_" + groupValue.replace("/", "_") + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            mSpinner.setVisible(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    new ReportCardGenerator().generateGroupReport(examId, groupBy, groupValue, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { mSpinner.setVisible(false); showInfo("Merit list PDF saved."); });
            task.setOnFailed(ev -> { mSpinner.setVisible(false); showAlert(task.getException().getMessage()); });
            new Thread(task).start();
        });

        content.getChildren().addAll(examRow, typeRow, btnRow, meritTable);
        tab.setContent(content);
        return tab;
    }

    private void loadMeritTable(long examId, String groupBy, String groupValue) {
        String filterCol = groupBy.equals("stream") ? "stream" : "form";

        // Subjects
        List<SubjInfo> subjects = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT sub.id, sub.subject_code, sub.subject_name FROM marks m JOIN subjects sub ON sub.id = m.subject_id WHERE m.exam_id = ? ORDER BY sub.subject_name")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) subjects.add(new SubjInfo(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        // Students + marks
        String sql = "SELECT s.id, s.admission_number, s.full_name, s.stream, m.subject_id, m.score, m.points_achieved FROM students s LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? WHERE s." + filterCol + " = ? ORDER BY s.id, m.subject_id";
        Map<Long, MeritBuilder> builders = new LinkedHashMap<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId); ps.setString(2, groupValue);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long sid = rs.getLong("id");
                String adm = rs.getString("admission_number");
                String name = rs.getString("full_name");
                String sStream = rs.getString("stream");
                MeritBuilder b = builders.computeIfAbsent(sid, k -> new MeritBuilder(adm, name, sStream));
                long subjId = rs.getLong("subject_id");
                if (!rs.wasNull()) {
                    b.scores.put(subjId, rs.getDouble("score"));
                    b.points.put(subjId, rs.getInt("points_achieved"));
                }
            }
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        // Subject means
        Map<Long, Double> means = new HashMap<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT subject_id, AVG(score) AS m FROM marks WHERE exam_id = ? GROUP BY subject_id")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) means.put(rs.getLong("subject_id"), rs.getDouble("m"));
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        // Subject positions
        Map<Long, List<Map.Entry<Long, Double>>> subjectScoreList = new HashMap<>();
        for (var entry : builders.entrySet()) {
            long sid = entry.getKey();
            for (var se : entry.getValue().scores.entrySet())
                subjectScoreList.computeIfAbsent(se.getKey(), k -> new ArrayList<>()).add(Map.entry(sid, se.getValue()));
        }
        Map<Long, Map<Long, Integer>> subjectPositions = new HashMap<>();
        for (var entry : subjectScoreList.entrySet()) {
            long subjId = entry.getKey();
            var list = entry.getValue();
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int rank = 1;
            double prev = Double.MAX_VALUE;
            for (int i = 0; i < list.size(); i++) {
                var e = list.get(i);
                if (e.getValue() < prev) rank = i + 1;
                prev = e.getValue();
                subjectPositions.computeIfAbsent(subjId, k -> new HashMap<>()).put(e.getKey(), rank);
            }
        }

        // Build rows
        List<MeritListRow> rows = new ArrayList<>();
        for (var entry : builders.entrySet()) {
            long sid = entry.getKey();
            MeritBuilder b = entry.getValue();
            double totalMarks = b.scores.values().stream().mapToDouble(v -> v).sum();
            int totalPoints = b.points.values().stream().mapToInt(v -> v).sum();
            int count = b.points.size();
            double meanPts = count > 0 ? Math.round((double) totalPoints / count * 10.0) / 10.0 : 0;
            String grade = meanPts >= 12 ? "A" : meanPts >= 11 ? "A-" : meanPts >= 10 ? "B+" : meanPts >= 9 ? "B" : meanPts >= 8 ? "B-" : meanPts >= 7 ? "C+" : meanPts >= 6 ? "C" : meanPts >= 5 ? "C-" : meanPts >= 4 ? "D+" : meanPts >= 3 ? "D" : meanPts >= 2 ? "D-" : "E";

            Map<Long, Double> devs = new HashMap<>();
            for (var se : b.scores.entrySet()) {
                double mean = means.getOrDefault(se.getKey(), 0.0);
                devs.put(se.getKey(), Math.round((se.getValue() - mean) * 10.0) / 10.0);
            }
            rows.add(new MeritListRow(sid, b.admissionNumber, b.fullName, b.stream,
                totalMarks, totalPoints, meanPts, grade, b.scores, devs, subjectPositions));
        }

        rows.sort((a, b) -> Integer.compare(b.totalPoints, a.totalPoints));
        int rank = 0, prevPts = Integer.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            MeritListRow r = rows.get(i);
            if (r.totalPoints < prevPts) rank = i + 1;
            prevPts = r.totalPoints;
            rows.set(i, new MeritListRow(r.studentId, r.admissionNumber, r.fullName, r.stream,
                r.totalMarks, r.totalPoints, r.meanPoints, r.meanGrade,
                r.scores, r.deviations, r.positions, rank));
        }

        // Build TableView
        meritTable.getColumns().clear();
        TableColumn<MeritListRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("rank")); cPos.setPrefWidth(35);
        TableColumn<MeritListRow, String> cAdm = new TableColumn<>("Adm");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdm.setPrefWidth(80);
        TableColumn<MeritListRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("fullName")); cName.setPrefWidth(140);
        meritTable.getColumns().addAll(cPos, cAdm, cName);

        for (SubjInfo si : subjects) {
            TableColumn<MeritListRow, String> parentCol = new TableColumn<>(si.code != null && !si.code.isBlank() ? si.code : si.name.substring(0, Math.min(4, si.name.length())));
            TableColumn<MeritListRow, Number> scrCol = new TableColumn<>("Scr");
            scrCol.setPrefWidth(45);
            scrCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().scores.getOrDefault(si.id, 0.0)));
            TableColumn<MeritListRow, Number> devCol = new TableColumn<>("Dev");
            devCol.setPrefWidth(45);
            devCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().deviations.getOrDefault(si.id, 0.0)));
            TableColumn<MeritListRow, Number> posCol = new TableColumn<>("Pos");
            posCol.setPrefWidth(35);
            long subjId = si.id;
            posCol.setCellValueFactory(cd -> {
                Map<Long, Integer> posMap = cd.getValue().positions.getOrDefault(subjId, new HashMap<>());
                return new SimpleObjectProperty<>(posMap.getOrDefault(cd.getValue().studentId, 0));
            });
            parentCol.getColumns().addAll(scrCol, devCol, posCol);
            meritTable.getColumns().add(parentCol);
        }

        TableColumn<MeritListRow, Number> cMarks = new TableColumn<>("T.Mks");
        cMarks.setCellValueFactory(new PropertyValueFactory<>("totalMarks")); cMarks.setPrefWidth(55);
        TableColumn<MeritListRow, Number> cPts = new TableColumn<>("Pts");
        cPts.setCellValueFactory(new PropertyValueFactory<>("totalPoints")); cPts.setPrefWidth(45);
        TableColumn<MeritListRow, Number> cMean = new TableColumn<>("Mean");
        cMean.setCellValueFactory(new PropertyValueFactory<>("meanPoints")); cMean.setPrefWidth(50);
        TableColumn<MeritListRow, String> cGrade = new TableColumn<>("Gr");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("meanGrade")); cGrade.setPrefWidth(40);
        meritTable.getColumns().addAll(cMarks, cPts, cMean, cGrade);

        meritTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void loadStreams(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM students WHERE stream IS NOT NULL ORDER BY stream")) {
            while (rs.next()) box.getItems().add(rs.getString("stream"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadForms(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM students WHERE form IS NOT NULL ORDER BY form")) {
            while (rs.next()) box.getItems().add(rs.getString("form"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    // ─────────── Merit List helper classes ───────────

    static class SubjInfo {
        final long id;
        final String code, name;
        SubjInfo(long id, String code, String name) { this.id = id; this.code = code; this.name = name; }
    }

    static class MeritBuilder {
        final String admissionNumber, fullName, stream;
        final Map<Long, Double> scores = new HashMap<>();
        final Map<Long, Integer> points = new HashMap<>();
        MeritBuilder(String adm, String name, String stream) { this.admissionNumber = adm; this.fullName = name; this.stream = stream; }
    }

    public static class MeritListRow {
        public final long studentId;
        public final int rank, totalPoints;
        public final String admissionNumber, fullName, stream, meanGrade;
        public final double totalMarks, meanPoints;
        public final Map<Long, Double> scores, deviations;
        public final Map<Long, Map<Long, Integer>> positions;

        public MeritListRow(long studentId, String admissionNumber, String fullName, String stream,
                            double totalMarks, int totalPoints, double meanPoints, String meanGrade,
                            Map<Long, Double> scores, Map<Long, Double> deviations,
                            Map<Long, Map<Long, Integer>> positions) {
            this(studentId, admissionNumber, fullName, stream, totalMarks, totalPoints, meanPoints, meanGrade,
                scores, deviations, positions, 0);
        }

        public MeritListRow(long studentId, String admissionNumber, String fullName, String stream,
                            double totalMarks, int totalPoints, double meanPoints, String meanGrade,
                            Map<Long, Double> scores, Map<Long, Double> deviations,
                            Map<Long, Map<Long, Integer>> positions, int rank) {
            this.studentId = studentId; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.stream = stream; this.totalMarks = totalMarks; this.totalPoints = totalPoints;
            this.meanPoints = meanPoints; this.meanGrade = meanGrade;
            this.scores = scores; this.deviations = deviations; this.positions = positions;
            this.rank = rank;
        }
        public int getRank() { return rank; }
        public String getAdmissionNumber() { return admissionNumber; }
        public String getFullName() { return fullName; }
        public String getStream() { return stream; }
        public double getTotalMarks() { return totalMarks; }
        public int getTotalPoints() { return totalPoints; }
        public double getMeanPoints() { return meanPoints; }
        public String getMeanGrade() { return meanGrade; }
    }

    // ────────────────────── Helpers ──────────────────────

    private void loadExams(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private static <T> TableColumn<T, ?> col(String title, String prop, int width) {
        TableColumn<T, ?> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    private void showAlert(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait()); }
    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait()); }

    // ────────────────────── Row Classes ──────────────────────

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
            this.classSize = classSize;
            this.prevTotalMarks = prevTotalMarks; this.prevPosition = prevPosition; this.positionChange = positionChange;
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
