package com.zaraki.exams.forms;

import com.zaraki.exams.repository.IStudentRepository;
import com.zaraki.exams.repository.StudentRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import static com.zaraki.exams.forms.AppTheme.PRIMARY;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import java.util.*;
import java.util.stream.Collectors;

public class RecycleBinForm {

    private final IStudentRepository studentRepo = new StudentRepositoryImpl();
    private final Runnable onBackToDashboard;
    private final StackPane root;

    public RecycleBinForm(Runnable onBackToDashboard) {
        this.onBackToDashboard = onBackToDashboard;
        this.root = new StackPane();
        showRecycledStudents();
    }

    public Node getView() {
        return root;
    }

    private void setView(Node node) {
        root.getChildren().setAll(node);
    }

    private void showRecycledStudents() {
        VBox view = new VBox(15);

        Button backBtn = new Button("\u2190 Back to Dashboard");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY + "; "
            + "-fx-font-size: 13; -fx-padding: 5 0 5 0;");
        backBtn.setOnAction(e -> onBackToDashboard.run());

        Label header = UIUtils.makeHeader("Recycle Bin");

        Label sub = new Label("These students have been deallocated. Restore them or permanently delete them.");

        HBox actions = new HBox(10);
        Button restoreBtn = new Button("Restore Selected");
        restoreBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        Button deleteBtn = new Button("Permanently Delete Selected");
        deleteBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");
        Button selectAllBtn = new Button("Select All");
        Button deselectBtn = new Button("Deselect All");
        Label statusLabel = UIUtils.makeStatusLabel();
        actions.getChildren().addAll(restoreBtn, deleteBtn, selectAllBtn, deselectBtn, statusLabel);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(24, 24);

        TableView<RecycleRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RecycleRow, Boolean> colSel = new TableColumn<>("\u2713");
        colSel.setPrefWidth(40);
        colSel.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        colSel.setCellFactory(col -> new CheckBoxTableCell<>());

        TableColumn<RecycleRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(60);

        TableColumn<RecycleRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        colAdm.setPrefWidth(140);

        TableColumn<RecycleRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(200);

        TableColumn<RecycleRow, Integer> colForm = new TableColumn<>("Form");
        colForm.setCellValueFactory(new PropertyValueFactory<>("form"));
        colForm.setPrefWidth(70);

        TableColumn<RecycleRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("streamName"));
        colStream.setPrefWidth(100);

        table.getColumns().addAll(colSel, colId, colAdm, colName, colForm, colStream);
        ObservableList<RecycleRow> data = FXCollections.observableArrayList();

        Task<Void> loadTask = new Task<>() {
            @Override protected Void call() {
                List<Map<String, Object>> rows = studentRepo.findAllDeallocated();
                Platform.runLater(() -> {
                    data.setAll(rows.stream().map(r -> new RecycleRow(
                        (long) r.get("id"),
                        (String) r.get("admission_number"),
                        (String) r.get("full_name"),
                        (int) r.get("form"),
                        (String) r.get("stream"))).toList());
                    table.setItems(data);
                    spinner.setVisible(false);
                    updateStatus(statusLabel, table);
                });
                return null;
            }
        };
        loadTask.setOnFailed(ev -> {
            spinner.setVisible(false);
            UIUtils.showError("Failed to load: " + loadTask.getException().getMessage());
        });
        new Thread(loadTask).start();

        selectAllBtn.setOnAction(e -> {
            for (RecycleRow row : table.getItems()) row.setSelected(true);
            table.refresh();
            updateStatus(statusLabel, table);
        });

        deselectBtn.setOnAction(e -> {
            for (RecycleRow row : table.getItems()) row.setSelected(false);
            table.refresh();
            updateStatus(statusLabel, table);
        });

        restoreBtn.setOnAction(e -> {
            List<RecycleRow> selected = data.stream().filter(RecycleRow::isSelected).collect(Collectors.toList());
            if (selected.isEmpty()) { UIUtils.showError("No students selected."); return; }
            Set<Long> ids = selected.stream().map(r -> r.id).collect(Collectors.toSet());
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    studentRepo.batchRestore(ids);
                    return null;
                }
            };
            task.setOnSucceeded(ev2 -> {
                data.removeIf(r -> ids.contains(r.id));
                table.setItems(data);
                updateStatus(statusLabel, table);
                UIUtils.showInfo("Restored " + selected.size() + " student(s).");
            });
            task.setOnFailed(ev2 -> UIUtils.showError("Error: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        deleteBtn.setOnAction(e -> {
            List<RecycleRow> selected = data.stream().filter(RecycleRow::isSelected).collect(Collectors.toList());
            if (selected.isEmpty()) { UIUtils.showError("No students selected."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete " + selected.size() + " student(s)?\n"
                + "This will also delete all their marks and cannot be undone!",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            Set<Long> ids = selected.stream().map(r -> r.id).collect(Collectors.toSet());
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    studentRepo.batchPermanentDelete(ids);
                    return null;
                }
            };
            task.setOnSucceeded(ev2 -> {
                data.removeIf(r -> ids.contains(r.id));
                table.setItems(data);
                updateStatus(statusLabel, table);
                UIUtils.showInfo("Permanently deleted " + selected.size() + " student(s).");
            });
            task.setOnFailed(ev2 -> UIUtils.showError("Error: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        VBox content = new VBox(10);
        content.getChildren().addAll(header, sub, actions, spinner, table);
        view.getChildren().addAll(backBtn, content);
        setView(view);
    }

    private void updateStatus(Label label, TableView<?> table) {
        int total = table.getItems().size();
        long sel = table.getItems().stream().filter(r -> r instanceof RecycleRow && ((RecycleRow)r).isSelected()).count();
        label.setText(sel + " of " + total + " selected");
    }

    public static class RecycleRow {
        private final long id;
        private final String admission, name, streamName;
        private final int form;
        private final javafx.beans.property.SimpleBooleanProperty selected;

        public RecycleRow(long id, String admission, String name, int form, String streamName) {
            this.id = id;
            this.admission = admission;
            this.name = name;
            this.form = form;
            this.streamName = streamName;
            this.selected = new javafx.beans.property.SimpleBooleanProperty(false);
        }

        public long getId() { return id; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public int getForm() { return form; }
        public String getStreamName() { return streamName; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean v) { selected.set(v); }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
    }

    private static class CheckBoxTableCell<S> extends TableCell<S, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        CheckBoxTableCell() {
            checkBox.setOnAction(e -> {
                RecycleRow row = (RecycleRow) getTableRow().getItem();
                if (row != null) row.setSelected(checkBox.isSelected());
            });
        }

        @Override protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                RecycleRow row = (RecycleRow) getTableRow().getItem();
                checkBox.setSelected(row.isSelected());
                setGraphic(checkBox);
            }
        }
    }
}
