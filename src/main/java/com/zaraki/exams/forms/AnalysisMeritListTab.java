package com.zaraki.exams.forms;

import com.zaraki.exams.reporting.ReportCardGenerator;
import com.zaraki.exams.service.IExamAnalysisService;
import com.zaraki.exams.util.UIUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class AnalysisMeritListTab {

    private final IExamAnalysisService service;
    private final Stage stage;
    private final ComboBox<String> meritExamBox = new ComboBox<>();
    private final ComboBox<String> meritGroupBox = new ComboBox<>();
    private final ToggleGroup meritGroupType = new ToggleGroup();
    private final RadioButton meritStreamRb = new RadioButton("Stream");
    private final RadioButton meritFormRb = new RadioButton("Form");
    private final TableView<IExamAnalysisService.MeritStudent> meritTable = new TableView<>();

    public AnalysisMeritListTab(IExamAnalysisService service, Stage stage) {
        this.service = service;
        this.stage = stage;
    }

    public Node getView() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        UIUtils.loadExams(meritExamBox);
        HBox examRow = new HBox(10, new Label("Exam:"), meritExamBox);
        meritExamBox.setPrefWidth(300);

        HBox typeRow = new HBox(10);
        meritStreamRb.setToggleGroup(meritGroupType);
        meritFormRb.setToggleGroup(meritGroupType);
        meritStreamRb.setSelected(true);
        meritGroupBox.setPrefWidth(200);
        UIUtils.loadStreamsWithForms(meritGroupBox);
        meritGroupType.selectedToggleProperty().addListener((obs, old, cur) -> {
            meritGroupBox.getItems().clear();
            if (cur == meritStreamRb) UIUtils.loadStreamsWithForms(meritGroupBox);
            else UIUtils.loadForms(meritGroupBox);
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
            if (meritExamBox.getValue() == null) { UIUtils.showError("Select exam."); return; }
            if (meritGroupBox.getValue() == null) { UIUtils.showError("Select group."); return; }
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupValue = meritGroupBox.getValue();
            mSpinner.setVisible(true);
            final int formFilter;
            final String groupBy;
            final String streamValue;
            if (meritStreamRb.isSelected() && groupValue.startsWith("Form ")) {
                String[] parts = groupValue.split(" ", 3);
                formFilter = Integer.parseInt(parts[1]);
                streamValue = parts[2];
                groupBy = "stream";
            } else {
                formFilter = 0;
                streamValue = groupValue;
                groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            }
            final String fGroupBy = groupBy;
            final String fStreamValue = streamValue;
            final int fFormFilter = formFilter;
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    loadMeritTable(examId, fGroupBy, fStreamValue, fFormFilter);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> mSpinner.setVisible(false));
            task.setOnFailed(ev -> { mSpinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        exportPdfBtn.setOnAction(e -> {
            if (meritExamBox.getValue() == null || meritGroupBox.getValue() == null) return;
            long examId = Long.parseLong(meritExamBox.getValue().split(" - ")[0]);
            String groupValue = meritGroupBox.getValue();
            final int formFilter;
            final String groupBy;
            final String streamValue;
            if (meritStreamRb.isSelected() && groupValue.startsWith("Form ")) {
                String[] parts = groupValue.split(" ", 3);
                formFilter = Integer.parseInt(parts[1]);
                streamValue = parts[2];
                groupBy = "stream";
            } else {
                formFilter = 0;
                streamValue = groupValue;
                groupBy = meritStreamRb.isSelected() ? "stream" : "form";
            }
            String fileNameBase = groupBy + "_" + (formFilter > 0 ? "F" + formFilter + "_" : "") + streamValue.replace("/", "_");
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Merit List");
            fc.setInitialFileName("merit_list_" + fileNameBase + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(stage);
            if (file == null) return;
            mSpinner.setVisible(true);
            final String fGroupBy = groupBy;
            final String fStreamValue = streamValue;
            final int fFormFilter = formFilter;
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    new ReportCardGenerator().generateGroupReport(examId, fGroupBy, fStreamValue, fFormFilter, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(ev -> { mSpinner.setVisible(false); UIUtils.showInfo("Merit list PDF saved."); });
            task.setOnFailed(ev -> { mSpinner.setVisible(false); UIUtils.showError(task.getException().getMessage()); });
            new Thread(task).start();
        });

        content.getChildren().addAll(examRow, typeRow, btnRow, meritTable);
        return content;
    }

    public void load(long examId) {
        // Merit list loads on user action only
    }

    private void loadMeritTable(long examId, String groupBy, String groupValue, int formFilter) {
        String filterCol = com.zaraki.exams.database.DatabaseEngine.validateFilterColumn(groupBy.equals("stream") ? "stream" : "form");
        IExamAnalysisService.MeritReportData data;
        try {
            data = service.computeMeritReport(examId, filterCol, groupValue, formFilter);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
            return;
        }
        java.util.List<IExamAnalysisService.MeritSubject> subjects = data.subjects();
        java.util.List<IExamAnalysisService.MeritStudent> students = data.students();
        meritTable.getColumns().clear();
        TableColumn<IExamAnalysisService.MeritStudent, String> cAdm = new TableColumn<>("Adm");
        cAdm.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().admissionNumber()));
        cAdm.setPrefWidth(80);
        TableColumn<IExamAnalysisService.MeritStudent, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().fullName()));
        cName.setPrefWidth(140);
        meritTable.getColumns().addAll(cAdm, cName);
        for (IExamAnalysisService.MeritSubject si : subjects) {
            String label = si.code() != null && !si.code().isBlank() ? si.code() : si.name().substring(0, Math.min(4, si.name().length()));
            TableColumn<IExamAnalysisService.MeritStudent, String> parentCol = new TableColumn<>(label);
            long subjId = si.id();
            TableColumn<IExamAnalysisService.MeritStudent, Number> scrCol = new TableColumn<>("Scr");
            scrCol.setPrefWidth(45);
            scrCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().scores().getOrDefault(subjId, 0.0)));
            TableColumn<IExamAnalysisService.MeritStudent, Number> posCol = new TableColumn<>("Pos");
            posCol.setPrefWidth(35);
            posCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().subjectPositions().getOrDefault(subjId, 0)));
            parentCol.getColumns().addAll(scrCol, posCol);
            meritTable.getColumns().add(parentCol);
        }
        TableColumn<IExamAnalysisService.MeritStudent, Number> cDeviation = new TableColumn<>("Dev");
        cDeviation.setPrefWidth(50);
        cDeviation.setCellValueFactory(cd -> {
            var devs = cd.getValue().deviations();
            double avg = devs.isEmpty() ? 0 : devs.values().stream().mapToDouble(v -> v).average().orElse(0);
            return new SimpleObjectProperty<>(Math.round(avg * 10.0) / 10.0);
        });
        TableColumn<IExamAnalysisService.MeritStudent, Number> cMarks = new TableColumn<>("T.Mks");
        cMarks.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().totalMarks()));
        cMarks.setPrefWidth(55);
        TableColumn<IExamAnalysisService.MeritStudent, Number> cPos = new TableColumn<>("Pos");
        cPos.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().rank()));
        cPos.setPrefWidth(40);
        TableColumn<IExamAnalysisService.MeritStudent, Number> cMean = new TableColumn<>("Mean");
        cMean.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().meanPoints()));
        cMean.setPrefWidth(50);
        TableColumn<IExamAnalysisService.MeritStudent, String> cGrade = new TableColumn<>("Gr");
        cGrade.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().meanGrade()));
        cGrade.setPrefWidth(40);
        meritTable.getColumns().addAll(cDeviation, cMarks, cPos, cMean, cGrade);
        meritTable.setItems(FXCollections.observableArrayList(students));
    }
}
