package com.zaraki.exams.forms;

import com.zaraki.exams.SeedData;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class DashboardDemoDataPanel {

    private final VBox view;
    private final ProgressIndicator spinner;
    private final Label status;
    private Runnable onDataChanged;

    public DashboardDemoDataPanel() {
        view = new VBox(8);
        view.setPadding(new Insets(15));
        view.getStyleClass().add("alert-box");

        Label demoLabel = new Label("\uD83D\uDD27  Demo Data Tools");
        demoLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        demoLabel.getStyleClass().add("alert-box-title");

        HBox btnRow = new HBox(10);
        Button seedStudentsBtn = new Button("\uD83D\uDC65 Generate Students (20/stream)");
        Button seedMarksBtn = new Button("\u270F\uFE0F Generate Marks for Exam 1");
        Button seedSubjectsBtn = new Button("\uD83D\uDCDA Generate Subjects");
        Button seedTeachersBtn = new Button("\uD83D\uDC68\u200D\uD83C\uDFEB Generate Teachers");
        Button resetBtn = new Button("\uD83D\uDDD1\uFE0F  Delete Entire Demo Database");
        resetBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");

        spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(20, 20);

        status = new Label();
        status.setFont(Font.font("System", 12));
        status.setTextFill(Color.gray(0.5));

        btnRow.getChildren().addAll(seedStudentsBtn, seedMarksBtn, seedSubjectsBtn, seedTeachersBtn, resetBtn, spinner);

        seedStudentsBtn.setOnAction(e -> seedAction("Generating students...", () -> {
            int count = new SeedData().seedAll();
            Platform.runLater(() -> { if (onDataChanged != null) onDataChanged.run(); });
            return "Generated " + count + " students across 4 forms.";
        }));

        seedMarksBtn.setOnAction(e -> seedAction("Generating marks...", () -> {
            SeedData sd = new SeedData();
            long eid = sd.getFirstExamId();
            if (eid < 0) return "No exams found. Create an exam first.";
            int count = sd.seedMarks(eid);
            Platform.runLater(() -> { if (onDataChanged != null) onDataChanged.run(); });
            return "Generated " + count + " marks for exam " + eid + ".";
        }));

        seedSubjectsBtn.setOnAction(e -> seedAction("Generating subjects...", () -> {
            new SeedData().seedSubjects();
            Platform.runLater(() -> { if (onDataChanged != null) onDataChanged.run(); });
            return "Subjects generated.";
        }));

        seedTeachersBtn.setOnAction(e -> seedAction("Generating teachers...", () -> {
            new SeedData().seedTeachers();
            Platform.runLater(() -> { if (onDataChanged != null) onDataChanged.run(); });
            return "Teachers generated.";
        }));

        resetBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete Entire Demo Database?\n\nThis will permanently delete ALL records.\nSettings and admin user will be preserved.\nThis CANNOT be undone!",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            seedAction("Resetting database...", () -> {
                new SeedData().hardReset();
                Platform.runLater(() -> { if (onDataChanged != null) onDataChanged.run(); });
                return "Demo database has been reset.";
            });
        });

        view.getChildren().addAll(demoLabel, btnRow, status);
    }

    public VBox getView() { return view; }
    public void setOnDataChanged(Runnable r) { this.onDataChanged = r; }

    private void seedAction(String loading, SeedAction action) {
        spinner.setVisible(true);
        status.setText(loading + "...");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return action.execute();
            }
        };
        task.setOnSucceeded(ev -> {
            spinner.setVisible(false);
            status.setText(task.getValue());
        });
        task.setOnFailed(ev -> {
            spinner.setVisible(false);
            status.setText("Error: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    @FunctionalInterface
    private interface SeedAction {
        String execute() throws Exception;
    }
}
