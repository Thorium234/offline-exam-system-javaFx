package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ReportForm {

    private static final String PRIMARY = "#1a237e";

    private final DatabaseEngine db;
    private final ReportCardGenerator reportGenerator;
    private final Stage stage;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final ComboBox<String> studentBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private final ComboBox<String> formBox = new ComboBox<>();
    private final ToggleGroup reportTypeGroup = new ToggleGroup();
    private final RadioButton individualRb = new RadioButton("Individual");
    private final RadioButton streamRb = new RadioButton("Stream");
    private final RadioButton classRb = new RadioButton("Class");
    private long selectedExamId;
    private long selectedStudentId;
    private String selectedStream;
    private String selectedForm;

    private VBox previewBox;
    private Label statusLabel;
    private TableView<MeritRow> broadsheetTable = new TableView<>();

    public ReportForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.reportGenerator = new ReportCardGenerator();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Reports");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        loadExams(examBox);

        HBox examRow = new HBox(10, new Label("Exam:"), examBox);

        // Report type radio buttons
        HBox typeRow = new HBox(15);
        typeRow.setPadding(new Insets(5, 0, 5, 0));
        for (RadioButton rb : new RadioButton[]{individualRb, streamRb, classRb}) {
            rb.setToggleGroup(reportTypeGroup);
            rb.setFont(Font.font("System", 13));
        }
        individualRb.setSelected(true);
        typeRow.getChildren().addAll(new Label("Report Type:"), individualRb, streamRb, classRb);

        // Selectors
        loadStudents(studentBox);
        loadStreams(streamBox);
        loadForms(formBox);

        HBox selectorRow = new HBox(10);
        Label studentLabel = new Label("Student:");
        studentBox.setPrefWidth(300);
        Label streamLabel = new Label("Stream:");
        streamBox.setPrefWidth(200);
        Label formLabel = new Label("Form:");
        formBox.setPrefWidth(100);

        selectorRow.getChildren().addAll(studentLabel, studentBox, streamLabel, streamBox, formLabel, formBox);
        streamBox.setVisible(false); streamLabel.setVisible(false);
        formBox.setVisible(false); formLabel.setVisible(false);

        // Buttons
        HBox btnRow = new HBox(10);
        Button previewBtn = new Button("Preview");
        Button genPdfBtn = new Button("Generate PDF");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);
        btnRow.getChildren().addAll(previewBtn, genPdfBtn, spinner);

        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.gray(0.4));

        // Preview area
        previewBox = new VBox(10);
        previewBox.setPadding(new Insets(15));
        previewBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        previewBox.setVisible(false);

        ScrollPane previewScroll = new ScrollPane(previewBox);
        previewScroll.setFitToWidth(true);
        previewScroll.setPrefHeight(500);
        previewScroll.setStyle("-fx-background-color: transparent;");
        previewScroll.setVisible(false);

        view.getChildren().addAll(header, examRow, typeRow, selectorRow, btnRow, statusLabel, previewScroll);

        // Toggle selector visibility
        reportTypeGroup.selectedToggleProperty().addListener((obs, old, cur) -> {
            boolean indiv = cur == individualRb;
            boolean stream = cur == streamRb;
            boolean cls = cur == classRb;
            studentLabel.setVisible(indiv); studentBox.setVisible(indiv);
            streamLabel.setVisible(stream); streamBox.setVisible(stream);
            formLabel.setVisible(cls); formBox.setVisible(cls);
            previewBox.setVisible(false);
            previewScroll.setVisible(false);
        });

        previewBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { showAlert("Select an exam."); return; }
            selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);

            if (individualRb.isSelected()) {
                if (studentBox.getValue() == null) { showAlert("Select a student."); return; }
                selectedStudentId = Long.parseLong(studentBox.getValue().split(" - ")[0]);
                spinner.setVisible(true);
                statusLabel.setText("Loading preview...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() { return null; }
                };
                task.setOnSucceeded(ev -> {
                    loadIndividualPreview(selectedExamId, selectedStudentId);
                    spinner.setVisible(false);
                    previewScroll.setVisible(true);
                    statusLabel.setText("Individual preview ready.");
                });
                new Thread(task).start();
            } else if (streamRb.isSelected()) {
                if (streamBox.getValue() == null) { showAlert("Select a stream."); return; }
                selectedStream = streamBox.getValue();
                spinner.setVisible(true);
                statusLabel.setText("Loading stream preview...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() { return null; }
                };
                task.setOnSucceeded(ev -> {
                    loadBroadsheetPreview(selectedExamId, "stream", selectedStream);
                    spinner.setVisible(false);
                    previewScroll.setVisible(true);
                    statusLabel.setText("Stream preview ready.");
                });
                new Thread(task).start();
            } else {
                if (formBox.getValue() == null) { showAlert("Select a form."); return; }
                selectedForm = formBox.getValue();
                spinner.setVisible(true);
                statusLabel.setText("Loading class preview...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() { return null; }
                };
                task.setOnSucceeded(ev -> {
                    loadBroadsheetPreview(selectedExamId, "form", selectedForm);
                    spinner.setVisible(false);
                    previewScroll.setVisible(true);
                    statusLabel.setText("Class preview ready.");
                });
                new Thread(task).start();
            }
        });

        genPdfBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { showAlert("Select an exam."); return; }
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Report");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

            if (individualRb.isSelected()) {
                if (studentBox.getValue() == null) { showAlert("Select a student."); return; }
                long studentId = Long.parseLong(studentBox.getValue().split(" - ")[0]);
                fc.setInitialFileName("report_card.pdf");
                File file = fc.showSaveDialog(stage);
                if (file == null) return;
                spinner.setVisible(true);
                statusLabel.setText("Generating PDF...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        reportGenerator.generateStudentReport(examId, studentId, file.toPath());
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Saved to: " + file.getName()); showInfo("Report saved."); });
                task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); showAlert(task.getException().getMessage()); });
                new Thread(task).start();
            } else if (streamRb.isSelected()) {
                if (streamBox.getValue() == null) { showAlert("Select a stream."); return; }
                fc.setInitialFileName("stream_report_" + streamBox.getValue().replace("/", "_") + ".pdf");
                File file = fc.showSaveDialog(stage);
                if (file == null) return;
                spinner.setVisible(true);
                statusLabel.setText("Generating stream report...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        reportGenerator.generateGroupReport(examId, "stream", streamBox.getValue(), file.toPath());
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Saved to: " + file.getName()); showInfo("Stream report saved."); });
                task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); showAlert(task.getException().getMessage()); });
                new Thread(task).start();
            } else {
                if (formBox.getValue() == null) { showAlert("Select a form."); return; }
                fc.setInitialFileName("form_report_" + formBox.getValue() + ".pdf");
                File file = fc.showSaveDialog(stage);
                if (file == null) return;
                spinner.setVisible(true);
                statusLabel.setText("Generating class report...");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        reportGenerator.generateGroupReport(examId, "form", formBox.getValue(), file.toPath());
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Saved to: " + file.getName()); showInfo("Class report saved."); });
                task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); showAlert(task.getException().getMessage()); });
                new Thread(task).start();
            }
        });

        return view;
    }

    // ─────────── Individual preview (existing) ───────────

    private void loadIndividualPreview(long examId, long studentId) {
        previewBox.getChildren().clear();

        Label title = new Label("THORIUM EXAM ANALYSIS SYSTEM");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(PRIMARY));
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        Label schoolLabel = new Label("Official Report Form - Kenya Secondary School");
        schoolLabel.setFont(Font.font("System", 12));
        schoolLabel.setAlignment(Pos.CENTER);
        schoolLabel.setMaxWidth(Double.MAX_VALUE);

        previewBox.getChildren().addAll(title, schoolLabel, new Separator());

        String examSql = "SELECT academic_year, term, exam_series FROM exams WHERE id = ?";
        String studentSql = "SELECT admission_number, full_name, form, stream FROM students WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement eps = conn.prepareStatement(examSql);
             PreparedStatement sps = conn.prepareStatement(studentSql)) {

            eps.setLong(1, examId);
            ResultSet er = eps.executeQuery();
            if (er.next())
                previewBox.getChildren().add(new Label("Exam: " + er.getString("academic_year") + " - " + er.getString("term") + " - " + er.getString("exam_series")));

            sps.setLong(1, studentId);
            ResultSet sr = sps.executeQuery();
            if (sr.next()) {
                previewBox.getChildren().add(new Label("Student: " + sr.getString("admission_number") + " - " + sr.getString("full_name")));
                previewBox.getChildren().add(new Label("Class: Form " + sr.getInt("form") + " - " + sr.getString("stream")));
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(new Separator());

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(5);
        grid.setPadding(new Insets(5, 0, 5, 0));
        grid.setStyle("-fx-background-color: " + PRIMARY + "; -fx-background-radius: 4; -fx-padding: 8;");
        String[] cols = {"Subject", "Score", "Grade", "Points", "Position", "Remarks"};
        for (int i = 0; i < cols.length; i++) {
            Label l = new Label(cols[i]);
            l.setFont(Font.font("System", FontWeight.BOLD, 11));
            l.setTextFill(Color.WHITE);
            grid.add(l, i, 0);
        }
        previewBox.getChildren().add(grid);

        String marksSql = """
            SELECT sub.subject_name, m.score, m.grade_achieved, m.points_achieved,
                (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                 WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id AND m2.score > m.score) AS pos,
                (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2
                 WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id) AS total_students
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            WHERE m.exam_id = ? AND m.student_id = ?
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(marksSql)) {
            ps.setLong(1, examId); ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            int row = 0;
            while (rs.next()) {
                GridPane rowGrid = new GridPane();
                rowGrid.setHgap(15);
                rowGrid.setPadding(new Insets(4, 8, 4, 8));
                if (row % 2 == 0) rowGrid.setStyle("-fx-background-color: #f9f9f9;");

                rowGrid.add(new Label(rs.getString("subject_name")), 0, 0);
                rowGrid.add(new Label(rs.getObject("score") != null ? String.valueOf(rs.getDouble("score")) : "-"), 1, 0);
                rowGrid.add(new Label(rs.getString("grade_achieved") != null ? rs.getString("grade_achieved") : "-"), 2, 0);
                rowGrid.add(new Label(rs.getObject("points_achieved") != null ? String.valueOf(rs.getInt("points_achieved")) : "-"), 3, 0);
                int pos = rs.getInt("pos");
                int total = rs.getInt("total_students");
                rowGrid.add(new Label(rs.wasNull() ? "-" : pos + "/" + total), 4, 0);
                rowGrid.add(new Label(""), 5, 0);
                previewBox.getChildren().add(rowGrid);
                row++;
            }
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(new Separator());

        VBox summary = new VBox(5);
        summary.setPadding(new Insets(8));
        summary.setStyle("-fx-background-color: #e8eaf6; -fx-background-radius: 6;");
        Label sumHeader = new Label("SUMMARY");
        sumHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        sumHeader.setTextFill(Color.web(PRIMARY));
        summary.getChildren().add(sumHeader);

        String sumSql = "SELECT ROUND(SUM(m.score), 1) AS total_marks, COALESCE(SUM(m.points_achieved), 0) AS total_points, ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points FROM marks m WHERE m.exam_id = ? AND m.student_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sumSql)) {
            ps.setLong(1, examId); ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                summary.getChildren().add(new Label("Total Marks: " + rs.getDouble("total_marks") + " | Total Points: " + rs.getInt("total_points") + " | Mean Points: " + rs.getDouble("mean_points")));
        } catch (SQLException e) { showAlert(e.getMessage()); }

        String trendSql = "SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points FROM marks m WHERE m.exam_id = ? AND m.student_id = ?";
        String prevSql = "SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points FROM marks m JOIN exams e ON e.id = m.exam_id WHERE m.student_id = ? AND e.academic_year = (SELECT academic_year FROM exams WHERE id = ?) AND e.id < ? ORDER BY e.id DESC LIMIT 1";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(trendSql); PreparedStatement pps = conn.prepareStatement(prevSql)) {
            ps.setLong(1, examId); ps.setLong(2, studentId);
            ResultSet cr = ps.executeQuery();
            int currentPoints = cr.next() ? cr.getInt("total_points") : 0;
            pps.setLong(1, studentId); pps.setLong(2, examId); pps.setLong(3, examId);
            ResultSet pr = pps.executeQuery();
            int prevPoints = pr.next() ? pr.getInt("total_points") : -1;
            Label trend = new Label();
            if (prevPoints >= 0) {
                int diff = currentPoints - prevPoints;
                if (diff > 0) trend.setText("Trend: Improved by " + diff + " pts from previous exam");
                else if (diff < 0) trend.setText("Trend: Dropped by " + Math.abs(diff) + " pts from previous exam");
                else trend.setText("Trend: Maintained same points as previous exam");
            } else trend.setText("Trend: First exam - no previous data.");
            trend.setFont(Font.font("System", FontWeight.BOLD, 11));
            summary.getChildren().add(trend);
        } catch (SQLException e) { showAlert(e.getMessage()); }

        previewBox.getChildren().add(summary);

        // Trend chart
        NumberAxis tx = new NumberAxis(); tx.setLabel("Exam #"); tx.setTickUnit(1); tx.setForceZeroInRange(false);
        NumberAxis ty = new NumberAxis(); ty.setLabel("Total Points"); ty.setForceZeroInRange(false);
        LineChart<Number, Number> trendChart = new LineChart<>(tx, ty);
        trendChart.setTitle("Performance Trend (All Exams)");
        trendChart.setPrefHeight(200); trendChart.setAnimated(false); trendChart.setLegendVisible(false);

        ExamAnalysisService eas = new ExamAnalysisService();
        List<ExamAnalysisService.StudentTrend> trendData = eas.computeStudentTrend(studentId);
        if (trendData.size() >= 2) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            for (int i = 0; i < trendData.size(); i++)
                series.getData().add(new XYChart.Data<>(i + 1, trendData.get(i).totalPoints()));
            trendChart.getData().add(series);
            double mn = trendData.stream().mapToDouble(ExamAnalysisService.StudentTrend::totalPoints).min().orElse(0);
            double mx = trendData.stream().mapToDouble(ExamAnalysisService.StudentTrend::totalPoints).max().orElse(0);
            double p = Math.max((mx - mn) * 0.15, 2);
            ty.setAutoRanging(false);
            ty.setLowerBound(Math.max(0, mn - p));
            ty.setUpperBound(mx + p);
            ty.setTickUnit(Math.max(1, (mx - mn + 2 * p) / 8));
        }
        VBox chartBox = new VBox(5, new Label("PERFORMANCE TREND"), trendChart);
        chartBox.setPadding(new Insets(10, 0, 0, 0));
        previewBox.getChildren().add(chartBox);

        previewBox.setVisible(true);
    }

    // ─────────── Merit List preview (stream/class with subject columns) ───────────

    private void loadBroadsheetPreview(long examId, String groupBy, String groupValue) {
        previewBox.getChildren().clear();

        Label title = new Label("THORIUM EXAM ANALYSIS SYSTEM");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(PRIMARY));
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        previewBox.getChildren().addAll(title, new Separator());

        final String examInfo;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT academic_year, term, exam_series FROM exams WHERE id = ?")) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            examInfo = rs.next() ? rs.getString("academic_year") + " - " + rs.getString("term") + " - " + rs.getString("exam_series") : "";
        } catch (SQLException e) { showAlert(e.getMessage()); return; }

        final String groupLabel = groupBy.equals("stream") ? "Stream: " + groupValue : "Form: " + groupValue;
        previewBox.getChildren().addAll(
            new Label("Exam: " + examInfo + "   |   " + groupLabel),
            new Label("MERIT LIST"),
            new Separator()
        );

        String filterCol = groupBy.equals("stream") ? "stream" : "form";
        Task<List<SubjectInfo>> subjTask = new Task<>() {
            @Override protected List<SubjectInfo> call() throws SQLException {
                List<SubjectInfo> list = new ArrayList<>();
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT DISTINCT sub.id, sub.subject_code, sub.subject_name FROM marks m JOIN subjects sub ON sub.id = m.subject_id WHERE m.exam_id = ? ORDER BY sub.subject_name")) {
                    ps.setLong(1, examId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next())
                        list.add(new SubjectInfo(rs.getLong("id"), rs.getString("subject_code"), rs.getString("subject_name")));
                }
                return list;
            }
        };

        Task<List<MeritRow>> dataTask = new Task<>() {
            @Override protected List<MeritRow> call() throws Exception {
                List<SubjectInfo> subjects = subjTask.get();

                // Fetch students + marks
                String sql = "SELECT s.id, s.admission_number, s.full_name, s.stream, m.subject_id, m.score, m.points_achieved FROM students s LEFT JOIN marks m ON m.student_id = s.id AND m.exam_id = ? WHERE s." + filterCol + " = ? ORDER BY s.id, m.subject_id";
                Map<Long, MeritRowBuilder> builders = new LinkedHashMap<>();
                try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, examId); ps.setString(2, groupValue);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        long sid = rs.getLong("id");
                        String adm = rs.getString("admission_number");
                        String name = rs.getString("full_name");
                        String sStream = rs.getString("stream");
                        MeritRowBuilder b = builders.computeIfAbsent(sid, k -> new MeritRowBuilder(adm, name, sStream));
                        long subjId = rs.getLong("subject_id");
                        if (!rs.wasNull()) {
                            b.scores.put(subjId, rs.getDouble("score"));
                            b.points.put(subjId, rs.getInt("points_achieved"));
                        }
                    }
                }

                // Subject means (exam-wide)
                Map<Long, Double> means = new HashMap<>();
                try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT subject_id, AVG(score) AS m FROM marks WHERE exam_id = ? GROUP BY subject_id")) {
                    ps.setLong(1, examId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) means.put(rs.getLong("subject_id"), rs.getDouble("m"));
                }

                // Subject positions (exam-wide)
                Map<Long, List<Map.Entry<Long, Double>>> subjectScoreList = new HashMap<>();
                for (var entry : builders.entrySet()) {
                    long sid = entry.getKey();
                    for (var se : entry.getValue().scores.entrySet()) {
                        subjectScoreList.computeIfAbsent(se.getKey(), k -> new ArrayList<>()).add(Map.entry(sid, se.getValue()));
                    }
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

                // Build MeritRow list
                List<MeritRow> result = new ArrayList<>();
                List<Map.Entry<Long, MeritRowBuilder>> sorted = new ArrayList<>(builders.entrySet());
                for (var entry : sorted) {
                    long sid = entry.getKey();
                    MeritRowBuilder b = entry.getValue();
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

                    result.add(new MeritRow(sid, b.admissionNumber, b.fullName, b.stream,
                        totalMarks, totalPoints, meanPts, grade,
                        b.scores, devs, subjectPositions));
                }
                // Sort by totalPoints descending, assign ranks
                result.sort((a, b) -> Integer.compare(b.totalPoints, a.totalPoints));
                int rank = 0, prevPts = Integer.MAX_VALUE;
                for (int i = 0; i < result.size(); i++) {
                    MeritRow r = result.get(i);
                    if (r.totalPoints < prevPts) rank = i + 1;
                    prevPts = r.totalPoints;
                    result.set(i, new MeritRow(r.studentId, r.admissionNumber, r.fullName, r.stream,
                        r.totalMarks, r.totalPoints, r.meanPoints, r.meanGrade,
                        r.scores, r.deviations, r.positions, rank));
                }
                return result;
            }
        };

        subjTask.setOnSucceeded(ev -> new Thread(dataTask).start());
        subjTask.setOnFailed(ev -> showAlert(subjTask.getException().getMessage()));
        new Thread(subjTask).start();

        dataTask.setOnSucceeded(ev -> {
            List<SubjectInfo> subjects = subjTask.getValue();
            List<MeritRow> rows = dataTask.getValue();
            buildMeritTable(subjects, rows, examInfo, groupLabel);
        });
        dataTask.setOnFailed(ev -> showAlert(dataTask.getException().getMessage()));

        previewBox.setVisible(true);
    }

    private void buildMeritTable(List<SubjectInfo> subjects, List<MeritRow> rows, String examInfo, String groupLabel) {
        broadsheetTable = new TableView<>();
        broadsheetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MeritRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("rank")); cPos.setPrefWidth(35);
        TableColumn<MeritRow, String> cAdm = new TableColumn<>("Adm");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdm.setPrefWidth(80);
        TableColumn<MeritRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("fullName")); cName.setPrefWidth(140);

        broadsheetTable.getColumns().addAll(cPos, cAdm, cName);

        for (SubjectInfo si : subjects) {
            TableColumn<MeritRow, String> parentCol = new TableColumn<>(si.code != null ? si.code : si.name.substring(0, Math.min(4, si.name.length())));

            TableColumn<MeritRow, Number> scrCol = new TableColumn<>("Scr");
            scrCol.setPrefWidth(45);
            long subjId = si.id;
            scrCol.setCellValueFactory(cd -> {
                MeritRow r = cd.getValue();
                return new SimpleObjectProperty<>(r.scores.getOrDefault(subjId, 0.0));
            });

            TableColumn<MeritRow, Number> devCol = new TableColumn<>("Dev");
            devCol.setPrefWidth(45);
            devCol.setCellValueFactory(cd -> {
                MeritRow r = cd.getValue();
                return new SimpleObjectProperty<>(r.deviations.getOrDefault(subjId, 0.0));
            });

            TableColumn<MeritRow, Number> posCol = new TableColumn<>("Pos");
            posCol.setPrefWidth(35);
            posCol.setCellValueFactory(cd -> {
                MeritRow r = cd.getValue();
                Map<Long, Integer> posMap = r.positions.getOrDefault(subjId, new HashMap<>());
                return new SimpleObjectProperty<>(posMap.getOrDefault(r.studentId, 0));
            });

            parentCol.getColumns().addAll(scrCol, devCol, posCol);
            broadsheetTable.getColumns().add(parentCol);
        }

        TableColumn<MeritRow, Number> cMarks = new TableColumn<>("T.Mks");
        cMarks.setCellValueFactory(new PropertyValueFactory<>("totalMarks")); cMarks.setPrefWidth(55);
        TableColumn<MeritRow, Number> cPts = new TableColumn<>("Pts");
        cPts.setCellValueFactory(new PropertyValueFactory<>("totalPoints")); cPts.setPrefWidth(45);
        TableColumn<MeritRow, Number> cMean = new TableColumn<>("Mean");
        cMean.setCellValueFactory(new PropertyValueFactory<>("meanPoints")); cMean.setPrefWidth(50);
        TableColumn<MeritRow, String> cGrade = new TableColumn<>("Gr");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("meanGrade")); cGrade.setPrefWidth(40);
        broadsheetTable.getColumns().addAll(cMarks, cPts, cMean, cGrade);

        ObservableList<MeritRow> data = FXCollections.observableArrayList(rows);
        broadsheetTable.setItems(data);
        previewBox.getChildren().add(broadsheetTable);

        Label footer = new Label("Total: " + rows.size() + " students  |  " + examInfo + "  |  " + groupLabel);
        footer.setFont(Font.font("System", FontWeight.BOLD, 12));
        footer.setTextFill(Color.gray(0.4));
        previewBox.getChildren().add(footer);
    }

    // ─────────── Helpers ───────────

    private void loadExams(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void loadStudents(ComboBox<String> box) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name FROM students ORDER BY full_name")) {
            while (rs.next())
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("admission_number") + " - " + rs.getString("full_name"));
        } catch (SQLException e) { showAlert(e.getMessage()); }
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

    private void showAlert(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait()); }
    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait()); }

    // ─────────── Merit List data classes ───────────

    public static class SubjectInfo {
        public final long id;
        public final String code, name;
        public SubjectInfo(long id, String code, String name) { this.id = id; this.code = code; this.name = name; }
    }

    static class MeritRowBuilder {
        final String admissionNumber, fullName, stream;
        final Map<Long, Double> scores = new HashMap<>();
        final Map<Long, Integer> points = new HashMap<>();
        MeritRowBuilder(String adm, String name, String stream) { this.admissionNumber = adm; this.fullName = name; this.stream = stream; }
    }

    public static class MeritRow {
        public final long studentId;
        public final int rank, totalPoints;
        public final String admissionNumber, fullName, stream, meanGrade;
        public final double totalMarks, meanPoints;
        public final Map<Long, Double> scores, deviations;
        public final Map<Long, Map<Long, Integer>> positions; // subjectId -> {studentId -> rank}

        public MeritRow(long studentId, String admissionNumber, String fullName, String stream,
                        double totalMarks, int totalPoints, double meanPoints, String meanGrade,
                        Map<Long, Double> scores, Map<Long, Double> deviations,
                        Map<Long, Map<Long, Integer>> positions) {
            this(studentId, admissionNumber, fullName, stream, totalMarks, totalPoints, meanPoints, meanGrade,
                scores, deviations, positions, 0);
        }

        public MeritRow(long studentId, String admissionNumber, String fullName, String stream,
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
}
