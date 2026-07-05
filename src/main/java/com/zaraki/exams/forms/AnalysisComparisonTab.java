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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class AnalysisComparisonTab {

    private final IExamAnalysisService service;
    private final Stage stage;
    private final ComboBox<String> exam1Box = new ComboBox<>();
    private final ComboBox<String> exam2Box = new ComboBox<>();
    private final TableView<ExamComparisonRow> table = new TableView<>();
    private ProgressIndicator spinner;

    public AnalysisComparisonTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        UIUtils.loadExams(exam1Box);
        UIUtils.loadExams(exam2Box);
        exam1Box.setPromptText("Earlier Exam");
        exam2Box.setPromptText("Later Exam");

        Button compareBtn = new Button("Compare");
        spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        HBox controls = new HBox(10, new Label("From:"), exam1Box, new Label("To:"), exam2Box, compareBtn, spinner);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ExamComparisonRow, Number> cPosC = new TableColumn<>("#");
        cPosC.setCellValueFactory(new PropertyValueFactory<>("rank")); cPosC.setPrefWidth(50);
        TableColumn<ExamComparisonRow, String> cAdm = new TableColumn<>("Admission");
        cAdm.setCellValueFactory(new PropertyValueFactory<>("admissionNumber")); cAdm.setPrefWidth(100);
        TableColumn<ExamComparisonRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("fullName")); cName.setPrefWidth(160);
        TableColumn<ExamComparisonRow, String> cForm = new TableColumn<>("Form");
        cForm.setCellValueFactory(new PropertyValueFactory<>("form")); cForm.setPrefWidth(55);
        TableColumn<ExamComparisonRow, String> cStream = new TableColumn<>("Stream");
        cStream.setCellValueFactory(new PropertyValueFactory<>("stream")); cStream.setPrefWidth(70);
        TableColumn<ExamComparisonRow, Number> cE1 = new TableColumn<>("Exam1 Pts");
        cE1.setCellValueFactory(new PropertyValueFactory<>("exam1Total")); cE1.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE1Pos = new TableColumn<>("Pos1");
        cE1Pos.setCellValueFactory(new PropertyValueFactory<>("exam1Pos")); cE1Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cE2 = new TableColumn<>("Exam2 Pts");
        cE2.setCellValueFactory(new PropertyValueFactory<>("exam2Total")); cE2.setPrefWidth(80);
        TableColumn<ExamComparisonRow, Number> cE2Pos = new TableColumn<>("Pos2");
        cE2Pos.setCellValueFactory(new PropertyValueFactory<>("exam2Pos")); cE2Pos.setPrefWidth(50);
        TableColumn<ExamComparisonRow, Number> cDiff = new TableColumn<>("\u0394 Pts");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difference")); cDiff.setPrefWidth(70);
        TableColumn<ExamComparisonRow, String> cPosDelta = new TableColumn<>("\u0394 Pos");
        cPosDelta.setCellValueFactory(new PropertyValueFactory<>("posChangeDisplay")); cPosDelta.setPrefWidth(60);
        table.getColumns().addAll(cPosC, cAdm, cName, cForm, cStream, cE1, cE1Pos, cE2, cE2Pos, cDiff, cPosDelta);

        compareBtn.setOnAction(e -> {
            if (exam1Box.getValue() == null || exam2Box.getValue() == null) return;
            long e1 = Long.parseLong(exam1Box.getValue().split(" - ")[0]);
            long e2 = Long.parseLong(exam2Box.getValue().split(" - ")[0]);
            spinner.setVisible(true);
            Task<List<IExamAnalysisService.ExamComparison>> task = new Task<>() {
                @Override protected List<IExamAnalysisService.ExamComparison> call() {
                    return service.compareExams(e1, e2);
                }
            };
            task.setOnSucceeded(ev -> {
                List<IExamAnalysisService.ExamComparison> list = task.getValue();
                ObservableList<ExamComparisonRow> rows = FXCollections.observableArrayList();
                int rank = 0;
                double prevDiff = Double.MAX_VALUE;
                for (int i = 0; i < list.size(); i++) {
                    IExamAnalysisService.ExamComparison ec = list.get(i);
                    if (ec.difference() < prevDiff) rank = i + 1;
                    prevDiff = ec.difference();
                    rows.add(new ExamComparisonRow(rank, ec.admissionNumber(), ec.fullName(),
                        ec.form(), ec.stream(), ec.exam1Total(), ec.exam2Total(), ec.difference(),
                        ec.exam1Pos(), ec.exam2Pos(), ec.posChange()));
                }
                table.setItems(rows);
                spinner.setVisible(false);
            });
            task.setOnFailed(ev -> { UIUtils.showError(task.getException().getMessage()); spinner.setVisible(false); });
            new Thread(task).start();
        });

        content.getChildren().addAll(controls, table);
        return content;
    }

    public void load(long examId) {
        // Comparison requires user to select two exams; no automatic load
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
}
