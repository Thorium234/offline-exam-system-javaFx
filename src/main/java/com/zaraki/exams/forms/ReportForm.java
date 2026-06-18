package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.service.ExamAnalysisService;
import javafx.application.Platform;
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
import java.util.List;
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
    private TableView<BroadsheetRow> broadsheetTable = new TableView<>();

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

    // ─────────── Broadsheet preview (stream/class) ───────────

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
            new Label("Exam: " + examInfo),
            new Label(groupLabel),
            new Separator()
        );

        broadsheetTable = new TableView<>();
        broadsheetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<BroadsheetRow, Number> cPos = new TableColumn<>("#");
        cPos.setCellValueFactory(new PropertyValueFactory<>("rank")); cPos.setPrefWidth(40);
        TableColumn<BroadsheetRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdm.setPrefWidth(100);
        TableColumn<BroadsheetRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("fullName")); cName.setPrefWidth(180);
        TableColumn<BroadsheetRow, String> cStream = new TableColumn<>("Stream");
        cStream.setCellValueFactory(new PropertyValueFactory<>("stream")); cStream.setPrefWidth(70);
        TableColumn<BroadsheetRow, Number> cMarks = new TableColumn<>("Marks");
        cMarks.setCellValueFactory(new PropertyValueFactory<>("totalMarks")); cMarks.setPrefWidth(70);
        TableColumn<BroadsheetRow, Number> cPoints = new TableColumn<>("Pts");
        cPoints.setCellValueFactory(new PropertyValueFactory<>("totalPoints")); cPoints.setPrefWidth(50);
        TableColumn<BroadsheetRow, Number> cMean = new TableColumn<>("Mean");
        cMean.setCellValueFactory(new PropertyValueFactory<>("meanPoints")); cMean.setPrefWidth(55);
        TableColumn<BroadsheetRow, String> cGrade = new TableColumn<>("Grade");
        cGrade.setCellValueFactory(new PropertyValueFactory<>("meanGrade")); cGrade.setPrefWidth(65);
        broadsheetTable.getColumns().addAll(cPos, cAdm, cName, cStream, cMarks, cPoints, cMean, cGrade);

        ExamAnalysisService eas = new ExamAnalysisService();
        Task<List<ExamAnalysisService.StudentResult>> task = new Task<>() {
            @Override protected List<ExamAnalysisService.StudentResult> call() {
                return eas.computeClassRankings(examId);
            }
        };
        task.setOnSucceeded(ev -> {
            List<ExamAnalysisService.StudentResult> all = task.getValue();
            List<ExamAnalysisService.StudentResult> filtered;
            if (groupBy.equals("stream"))
                filtered = all.stream().filter(r -> r.stream().equalsIgnoreCase(groupValue)).collect(Collectors.toList());
            else
                filtered = all.stream().filter(r -> r.form().equals(groupValue)).collect(Collectors.toList());

            ObservableList<BroadsheetRow> rows = FXCollections.observableArrayList();
            for (ExamAnalysisService.StudentResult r : filtered)
                rows.add(new BroadsheetRow(r.classRank(), r.admissionNumber(), r.fullName(),
                    r.stream(), r.totalMarks(), r.totalPoints(), r.meanPoints(), r.meanGrade()));
            broadsheetTable.setItems(rows);
            previewBox.getChildren().add(broadsheetTable);

            int count = filtered.size();
            Label footer = new Label("Total Students: " + count + "  |  Exam: " + examInfo + "  |  " + groupLabel);
            footer.setFont(Font.font("System", FontWeight.BOLD, 12));
            footer.setTextFill(Color.gray(0.4));
            previewBox.getChildren().add(footer);
        });
        task.setOnFailed(ev -> showAlert(task.getException().getMessage()));
        new Thread(task).start();

        previewBox.setVisible(true);
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

    public static class BroadsheetRow {
        private final int rank;
        private final String admissionNumber, fullName, stream, meanGrade;
        private final double totalMarks, meanPoints;
        private final int totalPoints;

        public BroadsheetRow(int rank, String admissionNumber, String fullName, String stream,
                             double totalMarks, int totalPoints, double meanPoints, String meanGrade) {
            this.rank = rank; this.admissionNumber = admissionNumber; this.fullName = fullName;
            this.stream = stream; this.totalMarks = totalMarks; this.totalPoints = totalPoints;
            this.meanPoints = meanPoints; this.meanGrade = meanGrade;
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
