package com.zaraki.exams.forms;

import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.repository.ExamRepository;
import com.zaraki.exams.repository.StudentRepository;
import com.zaraki.exams.service.ExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.util.List;

public class ReportForm {

    private static final String PRIMARY = AppTheme.PRIMARY;
    private static final int PAGE_SIZE = 20;

    private final DatabaseEngine db;
    private final ReportCardGenerator reportGenerator;
    private final ExamRepository examRepo;
    private final StudentRepository studentRepo;
    private final Stage stage;

    private final ComboBox<String> examBox = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label foundStudentLabel = new Label();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private final ComboBox<String> formBox = new ComboBox<>();
    private long selectedExamId;
    private long foundStudentId = -1;
    private boolean studentFound = false;
    private int currentPage = 0;
    private int totalResults = 0;
    private String lastQuery = "";
    private Label pageLabel = new Label();
    private Button prevPageBtn;
    private Button nextPageBtn;

    private VBox previewBox;
    private Label statusLabel;

    public ReportForm(DatabaseEngine db, Stage stage) {
        this.db = db;
        this.stage = stage;
        this.reportGenerator = new ReportCardGenerator();
        this.examRepo = new ExamRepository();
        this.studentRepo = new StudentRepository();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Reports — Report Cards");

        Label info = new Label("Generate report cards. Each student on a separate A4 page.");

        UIUtils.loadExams(examBox);
        UIUtils.loadStreams(streamBox);
        UIUtils.loadForms(formBox);

        HBox examRow = new HBox(10, new Label("Exam:"), examBox);
        examBox.setPrefWidth(400);

        Label singleLabel = new Label("Single Student");
        singleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox searchRow = new HBox(10);
        searchField.setPromptText("Search by Admission No. or Name");
        searchField.setPrefWidth(350);
        Button searchBtn = new Button("Search");
        Button genOneBtn = new Button("Generate PDF");
        foundStudentLabel.setFont(Font.font("System", 12));
        foundStudentLabel.setTextFill(Color.web(PRIMARY));

        prevPageBtn = new Button("\u25C0");
        prevPageBtn.setTooltip(new Tooltip("Previous page"));
        prevPageBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; searchStudent(currentPage); } });
        nextPageBtn = new Button("\u25B6");
        nextPageBtn.setTooltip(new Tooltip("Next page"));
        nextPageBtn.setOnAction(e -> { if ((currentPage + 1) * PAGE_SIZE < totalResults) { currentPage++; searchStudent(currentPage); } });

        searchRow.getChildren().addAll(searchField, searchBtn, prevPageBtn, pageLabel, nextPageBtn, genOneBtn);

        Separator sep = new Separator();
        Label bulkLabel = new Label("Generate for Stream / Form (multi-page PDF)");
        bulkLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox bulkRow = new HBox(10);
        streamBox.setPrefWidth(200);
        formBox.setPrefWidth(100);
        Button bulkGenBtn = new Button("Generate PDF for All");
        bulkRow.getChildren().addAll(new Label("Stream:"), streamBox, new Label(" Form:"), formBox, bulkGenBtn);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        statusLabel = UIUtils.makeStatusLabel();

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

        view.getChildren().addAll(header, info, examRow,
            singleLabel, searchRow, foundStudentLabel,
            sep, bulkLabel, bulkRow, spinner, statusLabel, previewScroll);

        searchBtn.setOnAction(e -> { currentPage = 0; searchStudent(0); });
        searchField.setOnAction(e -> { currentPage = 0; searchStudent(0); });

        genOneBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!examRepo.isReleased(examId)) { UIUtils.showError("This exam has not been released by the admin. Reports cannot be generated yet."); return; }
            if (!studentFound) { UIUtils.showError("Search and select a student first."); return; }

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Report Card");
            fc.setInitialFileName("report_card_" + foundStudentLabel.getText().split(" - ")[0].trim() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            spinner.setVisible(true);
            statusLabel.setText("Generating PDF...");
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    reportGenerator.generateStudentReport(examId, foundStudentId, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Saved: " + file.getName()); UIUtils.showInfo("Report saved."); });
            task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        foundStudentLabel.setOnMouseClicked(e -> {
            if (!studentFound || examBox.getValue() == null) return;
            selectedExamId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!examRepo.isReleased(selectedExamId)) { UIUtils.showError("Exam not released by admin. Preview unavailable."); return; }
            spinner.setVisible(true);
            statusLabel.setText("Loading preview...");
            Platform.runLater(() -> {
                loadIndividualPreview(selectedExamId, foundStudentId);
                spinner.setVisible(false);
                previewScroll.setVisible(true);
                statusLabel.setText("Preview ready.");
            });
        });

        bulkGenBtn.setOnAction(e -> {
            if (examBox.getValue() == null) { UIUtils.showError("Select an exam."); return; }
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            if (!examRepo.isReleased(examId)) { UIUtils.showError("This exam has not been released by the admin. Reports cannot be generated yet."); return; }
            String groupBy, groupValue;
            if (streamBox.getValue() != null && !streamBox.getValue().isBlank()) {
                groupBy = "stream"; groupValue = streamBox.getValue();
            } else if (formBox.getValue() != null && !formBox.getValue().isBlank()) {
                groupBy = "form"; groupValue = formBox.getValue();
            } else { UIUtils.showError("Select a stream or form."); return; }
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Bulk Report Cards");
            fc.setInitialFileName("report_cards_" + groupBy + "_" + groupValue.replace("/", "_") + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;

            spinner.setVisible(true);
            statusLabel.setText("Generating " + groupBy + " report cards...");
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    reportGenerator.generateBulkStudentReports(examId, groupBy, groupValue, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { spinner.setVisible(false); statusLabel.setText("Saved: " + file.getName() + " (" + groupValue + ")"); UIUtils.showInfo("Bulk report saved."); });
            task.setOnFailed(ev -> { spinner.setVisible(false); statusLabel.setText("Failed."); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        return view;
    }

    private void searchStudent(int page) {
        String q = searchField.getText().trim();
        if (q.isEmpty()) { UIUtils.showError("Enter a name or admission number."); return; }
        lastQuery = q;
        int offset = page * PAGE_SIZE;
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                var students = studentRepo.search(q, PAGE_SIZE, offset);
                int total = studentRepo.searchCount(q);
                Platform.runLater(() -> {
                    totalResults = total;
                    if (students.isEmpty()) {
                        foundStudentLabel.setText("No student found.");
                        studentFound = false;
                        pageLabel.setText("");
                        return;
                    }

                    var first = students.get(0);
                    long id = (Long) first.get("id");
                    String adm = (String) first.get("admission_number");
                    String name = (String) first.get("full_name");
                    int form = (Integer) first.get("form");
                    String stream = (String) first.get("stream");

                    if (students.size() == 1) {
                        foundStudentLabel.setText(adm + " - " + name + " (Form " + form + " " + stream + ") \u2014 click to preview");
                        foundStudentId = id;
                        studentFound = true;
                    } else {
                        StringBuilder sb = new StringBuilder("Page " + (page + 1) + ":\n");
                        for (var s : students) {
                            sb.append(s.get("id")).append(" - ").append(s.get("admission_number")).append(" - ").append(s.get("full_name")).append(" (Form ").append(s.get("form")).append(" ").append(s.get("stream")).append(")\n");
                        }
                        foundStudentLabel.setText(sb.toString());
                        foundStudentId = id;
                        studentFound = true;
                    }

                    int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
                    pageLabel.setText((page + 1) + "/" + totalPages);
                    prevPageBtn.setDisable(page <= 0);
                    nextPageBtn.setDisable(page >= totalPages - 1);
                });
                return null;
            }
        };
        task.setOnFailed(ev -> UIUtils.showError(task.getException().getMessage()));
        new Thread(task).start();
    }

    private void loadIndividualPreview(long examId, long studentId) {
        previewBox.getChildren().clear();

        String schoolName = new SettingsManager().getSchoolName();

        Label title = new Label("THORIUM EXAM ANALYSIS SYSTEM");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(PRIMARY));
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        Label schoolLabel = new Label("Official Report Form - " + schoolName);
        schoolLabel.setFont(Font.font("System", 12));
        schoolLabel.setAlignment(Pos.CENTER);
        schoolLabel.setMaxWidth(Double.MAX_VALUE);

        previewBox.getChildren().addAll(title, schoolLabel, new Separator());

        try {
            var exam = examRepo.findById(examId);
            if (exam != null)
                previewBox.getChildren().add(new Label("Exam: " + exam.get("academic_year") + " - " + exam.get("term") + " - " + exam.get("exam_series")));

            var student = studentRepo.findById(studentId);
            if (student != null) {
                previewBox.getChildren().add(new Label("Student: " + student.get("admission_number") + " - " + student.get("full_name")));
                previewBox.getChildren().add(new Label("Class: Form " + student.get("form") + " - " + student.get("stream")));
            }
        } catch (Exception e) { UIUtils.showError(e.getMessage()); }

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
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }

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
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }

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
        } catch (SQLException e) { UIUtils.showError(e.getMessage()); }

        previewBox.getChildren().add(summary);

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
}
