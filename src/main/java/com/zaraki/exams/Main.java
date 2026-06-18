package com.zaraki.exams;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.sql.*;

public class Main extends Application {

    private static final String PRIMARY = "#1a237e";
    private static final String BG = "#f5f5f5";
    private static final String CARD = "white";

    private final DatabaseEngine db = DatabaseEngine.getInstance();
    private StackPane contentArea;

    @Override
    public void start(Stage stage) {
        HBox root = new HBox();
        root.getChildren().addAll(createSidebar(), createMainContent());

        Scene scene = new Scene(root, 1200, 750);
        stage.setTitle("Zaraki Exam Analysis System");
        stage.setScene(scene);
        stage.show();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.setPrefWidth(230);
        sidebar.setPadding(new Insets(20, 12, 20, 12));
        sidebar.setStyle("-fx-background-color: " + PRIMARY + ";");

        Label title = new Label("ZARAKI");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("Exam Analysis");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.rgb(255, 255, 255, 0.6));
        sub.setPadding(new Insets(0, 0, 20, 0));

        VBox nav = new VBox(2);
        String[][] items = {
            {"Dashboard", ""},
            {"Students", ""},
            {"Subjects", ""},
            {"Exams", ""},
            {"Marks Entry", ""},
            {"Reports", ""}
        };

        for (String[] item : items) {
            Label lbl = new Label("  " + item[0]);
            lbl.setFont(Font.font("System", 14));
            lbl.setTextFill(Color.rgb(255, 255, 255, 0.75));
            lbl.setPadding(new Insets(10, 15, 10, 15));
            lbl.setPrefWidth(210);
            lbl.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");
            String page = item[0].toLowerCase().replace(" ", "_");
            lbl.setOnMouseEntered(e ->
                lbl.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 6;"));
            lbl.setOnMouseExited(e ->
                lbl.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;"));
            lbl.setOnMouseClicked(e -> navigate(page));
            nav.getChildren().add(lbl);
        }

        sidebar.getChildren().addAll(title, sub, nav);
        return sidebar;
    }

    private ScrollPane createMainContent() {
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: " + BG + ";");
        contentArea.setPadding(new Insets(30, 40, 30, 40));

        showDashboard();

        ScrollPane sp = new ScrollPane(contentArea);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent; -fx-border: none;");
        return sp;
    }

    private void navigate(String page) {
        switch (page) {
            case "dashboard" -> showDashboard();
            case "students" -> showStudents();
            case "subjects" -> showSubjects();
            case "exams" -> showExams();
            case "marks_entry" -> showMarksEntry();
            case "reports" -> showReports();
        }
    }

    // ========== DASHBOARD ==========

    private void showDashboard() {
        VBox view = new VBox(20);

        Label header = new Label("Dashboard");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        int studentCount = count("students");
        int subjectCount = count("subjects");
        int examCount = count("exams");
        int markCount = count("marks");

        HBox cards = new HBox(20);
        cards.getChildren().addAll(
            statCard("Students", String.valueOf(studentCount)),
            statCard("Subjects", String.valueOf(subjectCount)),
            statCard("Exams", String.valueOf(examCount)),
            statCard("Marks Entered", String.valueOf(markCount))
        );

        Label welcome = new Label("Welcome to Zaraki Exam Analysis System. Use the sidebar to navigate.");
        welcome.setFont(Font.font("System", 14));
        welcome.setTextFill(Color.gray(0.4));
        welcome.setWrapText(true);

        view.getChildren().addAll(header, welcome, cards);
        contentArea.getChildren().setAll(view);
    }

    private VBox statCard(String title, String value) {
        VBox card = new VBox(5);
        card.setPrefSize(220, 100);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 10; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        Label val = new Label(value);
        val.setFont(Font.font("System", FontWeight.BOLD, 30));
        val.setTextFill(Color.web(PRIMARY));
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", 13));
        lbl.setTextFill(Color.gray(0.5));
        card.getChildren().addAll(val, lbl);
        return card;
    }

    // ========== STUDENTS ==========

    private void showStudents() {
        VBox view = new VBox(15);

        Label header = new Label("Students");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField admField = new TextField();
        admField.setPromptText("Admission No.");
        TextField nameField = new TextField();
        nameField.setPromptText("Full Name");
        TextField formField = new TextField();
        formField.setPromptText("Form");
        TextField streamField = new TextField();
        streamField.setPromptText("Stream");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(admField, nameField, formField, streamField, addBtn);

        TableView<StudentRow> table = new TableView<>();
        TableColumn<StudentRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(60);
        TableColumn<StudentRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        colAdm.setPrefWidth(140);
        TableColumn<StudentRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(250);
        TableColumn<StudentRow, Integer> colForm = new TableColumn<>("Form");
        colForm.setCellValueFactory(new PropertyValueFactory<>("form"));
        colForm.setPrefWidth(70);
        TableColumn<StudentRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("stream"));
        colStream.setPrefWidth(120);
        table.getColumns().addAll(colId, colAdm, colName, colForm, colStream);
        table.setPrefHeight(400);

        ObservableList<StudentRow> data = FXCollections.observableArrayList();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name, form, stream FROM students")) {
            while (rs.next())
                data.add(new StudentRow(rs.getLong("id"), rs.getString("admission_number"),
                    rs.getString("full_name"), rs.getInt("form"), rs.getString("stream")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)")) {
                ps.setString(1, admField.getText());
                ps.setString(2, nameField.getText());
                ps.setInt(3, Integer.parseInt(formField.getText()));
                ps.setString(4, streamField.getText());
                ps.executeUpdate();
                data.add(new StudentRow(0L, admField.getText(), nameField.getText(),
                    Integer.parseInt(formField.getText()), streamField.getText()));
                admField.clear(); nameField.clear(); formField.clear(); streamField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        contentArea.getChildren().setAll(view);
    }

    // ========== SUBJECTS ==========

    private void showSubjects() {
        VBox view = new VBox(15);
        Label header = new Label("Subjects");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField codeField = new TextField(); codeField.setPromptText("Code");
        TextField nameField = new TextField(); nameField.setPromptText("Name");
        TextField deptField = new TextField(); deptField.setPromptText("Department");
        ComboBox<String> grpBox = new ComboBox<>(FXCollections.observableArrayList("Compulsory", "Elective"));
        grpBox.setPromptText("Grouping");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(codeField, nameField, deptField, grpBox, addBtn);

        TableView<SubjectRow> table = new TableView<>();
        TableColumn<SubjectRow, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        cCode.setPrefWidth(100);
        TableColumn<SubjectRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("name"));
        cName.setPrefWidth(250);
        TableColumn<SubjectRow, String> cDept = new TableColumn<>("Department");
        cDept.setCellValueFactory(new PropertyValueFactory<>("dept"));
        cDept.setPrefWidth(150);
        TableColumn<SubjectRow, String> cGrp = new TableColumn<>("Grouping");
        cGrp.setCellValueFactory(new PropertyValueFactory<>("grouping"));
        cGrp.setPrefWidth(120);
        table.getColumns().addAll(cCode, cName, cDept, cGrp);
        table.setPrefHeight(400);

        ObservableList<SubjectRow> data = FXCollections.observableArrayList();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT subject_code, subject_name, department, grouping FROM subjects")) {
            while (rs.next())
                data.add(new SubjectRow(rs.getString("subject_code"), rs.getString("subject_name"),
                    rs.getString("department"), rs.getString("grouping")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)")) {
                ps.setString(1, codeField.getText());
                ps.setString(2, nameField.getText());
                ps.setString(3, deptField.getText());
                ps.setString(4, grpBox.getValue());
                ps.executeUpdate();
                data.add(new SubjectRow(codeField.getText(), nameField.getText(),
                    deptField.getText(), grpBox.getValue()));
                codeField.clear(); nameField.clear(); deptField.clear(); grpBox.setValue(null);
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        contentArea.getChildren().setAll(view);
    }

    // ========== EXAMS ==========

    private void showExams() {
        VBox view = new VBox(15);
        Label header = new Label("Exams");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField yearField = new TextField(); yearField.setPromptText("Year (e.g. 2026)");
        ComboBox<String> termBox = new ComboBox<>(FXCollections.observableArrayList("Term 1", "Term 2", "Term 3"));
        termBox.setPromptText("Term");
        TextField seriesField = new TextField(); seriesField.setPromptText("Series (e.g. End-Term)");
        Button addBtn = new Button("Create");
        form.getChildren().addAll(yearField, termBox, seriesField, addBtn);

        TableView<ExamRow> table = new TableView<>();
        TableColumn<ExamRow, Long> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id"));
        cId.setPrefWidth(60);
        TableColumn<ExamRow, String> cYear = new TableColumn<>("Year");
        cYear.setCellValueFactory(new PropertyValueFactory<>("year"));
        cYear.setPrefWidth(120);
        TableColumn<ExamRow, String> cTerm = new TableColumn<>("Term");
        cTerm.setCellValueFactory(new PropertyValueFactory<>("term"));
        cTerm.setPrefWidth(120);
        TableColumn<ExamRow, String> cSeries = new TableColumn<>("Series");
        cSeries.setCellValueFactory(new PropertyValueFactory<>("series"));
        cSeries.setPrefWidth(200);
        table.getColumns().addAll(cId, cYear, cTerm, cSeries);
        table.setPrefHeight(400);

        ObservableList<ExamRow> data = FXCollections.observableArrayList();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams")) {
            while (rs.next())
                data.add(new ExamRow(rs.getLong("id"), rs.getString("academic_year"),
                    rs.getString("term"), rs.getString("exam_series")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)")) {
                ps.setString(1, yearField.getText());
                ps.setString(2, termBox.getValue());
                ps.setString(3, seriesField.getText());
                ps.executeUpdate();
                data.add(new ExamRow(0L, yearField.getText(), termBox.getValue(), seriesField.getText()));
                yearField.clear(); termBox.setValue(null); seriesField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        contentArea.getChildren().setAll(view);
    }

    // ========== MARKS ENTRY ==========

    private void showMarksEntry() {
        VBox view = new VBox(15);
        Label header = new Label("Marks Entry");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Select an exam and enter marks for each student and subject.");
        info.setFont(Font.font("System", 14));
        info.setTextFill(Color.gray(0.4));

        HBox row = new HBox(10);
        ComboBox<String> examBox = new ComboBox<>();
        examBox.setPromptText("Select Exam");
        examBox.setPrefWidth(250);
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams")) {
            while (rs.next())
                examBox.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year")
                    + " " + rs.getString("term") + " " + rs.getString("exam_series"));
        } catch (SQLException e) { showAlert(e.getMessage()); }

        TextField scoreField = new TextField();
        scoreField.setPromptText("Score");
        scoreField.setPrefWidth(100);
        Button saveBtn = new Button("Save Score");

        TableView<MarkRow> table = new TableView<>();
        TableColumn<MarkRow, String> cSName = new TableColumn<>("Student");
        cSName.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        cSName.setPrefWidth(200);
        TableColumn<MarkRow, String> cSubName = new TableColumn<>("Subject");
        cSubName.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
        cSubName.setPrefWidth(200);
        TableColumn<MarkRow, Double> cScore = new TableColumn<>("Score");
        cScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        cScore.setPrefWidth(100);
        table.getColumns().addAll(cSName, cSubName, cScore);
        table.setPrefHeight(400);

        view.getChildren().addAll(header, info, row, table, saveBtn);

        row.getChildren().addAll(examBox, scoreField);

        saveBtn.setOnAction(e -> {
            MarkRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String examStr = examBox.getValue();
            if (examStr == null) return;
            long examId = Long.parseLong(examStr.split(" - ")[0]);
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score) VALUES (?,?,(SELECT id FROM subjects WHERE subject_name=?),?)")) {
                ps.setLong(1, examId);
                ps.setString(3, selected.subjectName);
                ps.setDouble(4, Double.parseDouble(scoreField.getText()));

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT id FROM students WHERE full_name='" + selected.studentName + "'")) {
                    if (rs.next()) ps.setLong(2, rs.getLong("id"));
                    else return;
                }
                ps.executeUpdate();
                selected.score = Double.parseDouble(scoreField.getText());
                table.refresh();
                scoreField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        examBox.setOnAction(e -> {
            if (examBox.getValue() == null) return;
            long examId = Long.parseLong(examBox.getValue().split(" - ")[0]);
            ObservableList<MarkRow> marks = FXCollections.observableArrayList();
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT s.full_name, sub.subject_name, m.score " +
                     "FROM students s CROSS JOIN subjects sub " +
                     "LEFT JOIN marks m ON m.student_id = s.id AND m.subject_id = sub.id AND m.exam_id = " + examId)) {
                while (rs.next())
                    marks.add(new MarkRow(rs.getString("full_name"), rs.getString("subject_name"),
                        rs.getObject("score") != null ? rs.getDouble("score") : null));
            } catch (SQLException ex) { showAlert(ex.getMessage()); }
            table.setItems(marks);
        });

        contentArea.getChildren().setAll(view);
    }

    // ========== REPORTS ==========

    private void showReports() {
        VBox view = new VBox(15);
        Label header = new Label("Reports");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        Label msg = new Label("Report generation (PDF) will be available in a future update.");
        msg.setFont(Font.font("System", 14));
        msg.setTextFill(Color.gray(0.4));
        view.getChildren().addAll(header, msg);
        contentArea.getChildren().setAll(view);
    }

    // ========== HELPERS ==========

    private int count(String table) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ========== ROW CLASSES ==========

    public static class StudentRow {
        private final Long id;
        private final String admission;
        private final String name;
        private final Integer form;
        private final String stream;
        public StudentRow(Long id, String admission, String name, Integer form, String stream) {
            this.id = id; this.admission = admission; this.name = name;
            this.form = form; this.stream = stream;
        }
        public Long getId() { return id; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Integer getForm() { return form; }
        public String getStream() { return stream; }
    }

    public static class SubjectRow {
        private final String code, name, dept, grouping;
        public SubjectRow(String code, String name, String dept, String grouping) {
            this.code = code; this.name = name; this.dept = dept; this.grouping = grouping;
        }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDept() { return dept; }
        public String getGrouping() { return grouping; }
    }

    public static class ExamRow {
        private final Long id;
        private final String year, term, series;
        public ExamRow(Long id, String year, String term, String series) {
            this.id = id; this.year = year; this.term = term; this.series = series;
        }
        public Long getId() { return id; }
        public String getYear() { return year; }
        public String getTerm() { return term; }
        public String getSeries() { return series; }
    }

    public static class MarkRow {
        private final String studentName, subjectName;
        private Double score;
        public MarkRow(String studentName, String subjectName, Double score) {
            this.studentName = studentName; this.subjectName = subjectName; this.score = score;
        }
        public String getStudentName() { return studentName; }
        public String getSubjectName() { return subjectName; }
        public Double getScore() { return score; }
    }
}
