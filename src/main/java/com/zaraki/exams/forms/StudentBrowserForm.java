package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.UIUtils;
import static com.zaraki.exams.forms.AppTheme.PRIMARY;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StudentBrowserForm {

    private static final String CARD_STYLE = "-fx-background-color: white; -fx-background-radius: 10; "
        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);";

    private final DatabaseEngine db;
    private final Runnable onBackToDashboard;
    private final StackPane root;

    public StudentBrowserForm(DatabaseEngine db, Runnable onBackToDashboard) {
        this.db = db;
        this.onBackToDashboard = onBackToDashboard;
        this.root = new StackPane();
        showFormSelection();
    }

    public Node getView() {
        return root;
    }

    private void setView(Node node) {
        root.getChildren().setAll(node);
    }

    // ───── Form Selection (Form 1-4 Cards) ─────

    private void showFormSelection() {
        VBox view = new VBox(20);

        Button backBtn = new Button("← Back to Dashboard");
        styleBackBtn(backBtn);
        backBtn.setOnAction(e -> onBackToDashboard.run());

        Label header = UIUtils.makeHeader("Browse Students");

        Label sub = new Label("Select a form to view its students");
        sub.setFont(Font.font("System", 14));
        sub.setTextFill(Color.gray(0.5));

        HBox cards = new HBox(20);
        cards.setAlignment(Pos.CENTER);
        for (int form = 1; form <= 4; form++) {
            VBox card = new VBox(5);
            card.setPrefSize(200, 140);
            card.setAlignment(Pos.CENTER);
            card.setStyle(CARD_STYLE);
            Label num = new Label(String.valueOf(form));
            num.setFont(Font.font("System", FontWeight.BOLD, 48));
            num.setTextFill(Color.web(PRIMARY));
            Label lbl = new Label("Form " + form);
            lbl.setFont(Font.font("System", 14));
            lbl.setTextFill(Color.gray(0.5));
            card.getChildren().addAll(num, lbl);

            int f = form;
            card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: #e8eaf6; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
            ));
            card.setOnMouseExited(e -> card.setStyle(CARD_STYLE));
            card.setOnMouseClicked(e -> showStudentList(f));
            cards.getChildren().add(card);
        }

        view.getChildren().addAll(backBtn, header, sub, cards);
        setView(view);
    }

    // ───── Student List with Stream Filter & Pagination ─────

    private static final int PAGE_SIZE = 20;

    private void showStudentList(int form) {
        VBox view = new VBox(15);

        Button backBtn = new Button("← Back to Forms");
        styleBackBtn(backBtn);
        backBtn.setOnAction(e -> showFormSelection());

        Label header = new Label("Form " + form + " Students");
        header.setFont(Font.font("System", FontWeight.BOLD, 22));

        HBox streamCards = new HBox(10);
        Label streamLabel = new Label("Stream:");
        streamLabel.setFont(Font.font("System", 13));
        streamLabel.setTextFill(Color.gray(0.5));
        streamCards.getChildren().add(streamLabel);

        HBox actions = new HBox(10);
        Button deallocateBtn = new Button("Deallocate Selected");
        deallocateBtn.getStyleClass().addAll("button", "button-danger");
        Button selectAllBtn = new Button("Select All");
        Button deselectBtn = new Button("Deselect All");
        Label statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setTextFill(Color.gray(0.5));
        actions.getChildren().addAll(deallocateBtn, selectAllBtn, deselectBtn, statusLabel);

        TableView<StudentRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudentRow, Boolean> colSel = new TableColumn<>("✓");
        colSel.setPrefWidth(40);
        colSel.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        colSel.setCellFactory(col -> new CheckBoxTableCell<>());

        TableColumn<StudentRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(60);

        TableColumn<StudentRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission"));
        colAdm.setPrefWidth(140);

        TableColumn<StudentRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setPrefWidth(250);

        TableColumn<StudentRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("streamName"));
        colStream.setPrefWidth(100);

        table.getColumns().addAll(colSel, colId, colAdm, colName, colStream);
        ObservableList<StudentRow> data = FXCollections.observableArrayList();

        // Pagination state
        final int[] currentPage = {1};
        final int[] totalCount = {0};
        Label pageLabel = new Label();
        pageLabel.setFont(Font.font("System", 12));
        pageLabel.setTextFill(Color.gray(0.5));
        Button prevBtn = new Button("◀ Previous");
        Button nextBtn = new Button("Next ▶");
        prevBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white; -fx-font-size: 11;");
        nextBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white; -fx-font-size: 11;");
        prevBtn.setDisable(true);
        nextBtn.setDisable(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(24, 24);

        // Shared load function
        java.util.function.BiConsumer<Integer, Integer> loadPage = new java.util.function.BiConsumer<>() {
            @Override public void accept(Integer page, Integer formVal) {
                int offset = (page - 1) * PAGE_SIZE;
                spinner.setVisible(true);
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        List<String> streams = new ArrayList<>();
                        List<StudentRow> rows = new ArrayList<>();
                        int total = 0;
                        try (Connection conn = db.getConnection()) {
                            try (PreparedStatement countPs = conn.prepareStatement(
                                    "SELECT COUNT(*) FROM students WHERE form = ? AND deallocated = 0")) {
                                countPs.setInt(1, formVal);
                                ResultSet crs = countPs.executeQuery();
                                if (crs.next()) total = crs.getInt(1);
                            }
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "SELECT id, admission_number, full_name, stream FROM students "
                                    + "WHERE form = ? AND deallocated = 0 ORDER BY admission_number LIMIT ? OFFSET ?")) {
                                ps.setInt(1, formVal);
                                ps.setInt(2, PAGE_SIZE);
                                ps.setInt(3, offset);
                                try (ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) {
                                        String stream = rs.getString("stream");
                                        if (!streams.contains(stream)) streams.add(stream);
                                        rows.add(new StudentRow(rs.getLong("id"),
                                            rs.getString("admission_number"),
                                            rs.getString("full_name"),
                                            stream));
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
                        int finalTotal = total;
                        javafx.application.Platform.runLater(() -> {
                            streamCards.getChildren().removeIf(n -> n instanceof HBox);
                            for (String s : streams) {
                                HBox streamItem = new HBox(5);
                                ToggleButton tb = new ToggleButton(s);
                                tb.setStyle("-fx-background-color: white; -fx-border-color: " + PRIMARY + ";"
                                    + " -fx-border-radius: 15; -fx-background-radius: 15;"
                                    + " -fx-text-fill: " + PRIMARY + "; -fx-font-size: 12;"
                                    + " -fx-padding: 5 15 5 15;");
                                tb.setOnAction(ev -> {
                                    if (tb.isSelected()) {
                                        streamCards.getChildren().stream()
                                            .filter(n -> n instanceof HBox)
                                            .flatMap(h -> ((HBox)h).getChildren().stream())
                                            .filter(n -> n instanceof ToggleButton && n != tb)
                                            .forEach(n -> ((ToggleButton)n).setSelected(false));
                                        table.setItems(data.filtered(r -> r.streamName.equals(s)));
                                    } else {
                                        table.setItems(data);
                                    }
                                    updateStatus(statusLabel, table);
                                });
                                Button subjBtn = new Button("Subjects");
                                subjBtn.setStyle("-fx-background-color: " + PRIMARY + "; -fx-text-fill: white;"
                                    + " -fx-font-size: 10; -fx-padding: 4 10 4 10; -fx-background-radius: 12;");
                                int f = formVal;
                                String fs = s;
                                subjBtn.setOnAction(ev -> showSubjectAssignment(f, fs));
                                streamItem.getChildren().addAll(tb, subjBtn);
                                streamCards.getChildren().add(streamItem);
                            }
                            data.setAll(rows);
                            table.setItems(data);
                            spinner.setVisible(false);
                            pageLabel.setText("Page " + page + " of " + totalPages + " (" + finalTotal + " total)");
                            prevBtn.setDisable(page <= 1);
                            nextBtn.setDisable(page >= totalPages);
                            updateStatus(statusLabel, table);
                        });
                        return null;
                    }
                };
                task.setOnFailed(ev -> {
                    spinner.setVisible(false);
                    UIUtils.showError("Failed to load: " + task.getException().getMessage());
                });
                new Thread(task).start();
            }
        };

        prevBtn.setOnAction(e -> {
            if (currentPage[0] > 1) {
                currentPage[0]--;
                loadPage.accept(currentPage[0], form);
            }
        });
        nextBtn.setOnAction(e -> {
            currentPage[0]++;
            loadPage.accept(currentPage[0], form);
        });

        loadPage.accept(1, form);

        selectAllBtn.setOnAction(e -> {
            for (StudentRow row : table.getItems()) row.setSelected(true);
            table.refresh();
            updateStatus(statusLabel, table);
        });

        deselectBtn.setOnAction(e -> {
            for (StudentRow row : table.getItems()) row.setSelected(false);
            table.refresh();
            updateStatus(statusLabel, table);
        });

        deallocateBtn.setOnAction(e -> {
            List<StudentRow> selected = data.stream().filter(StudentRow::isSelected).collect(Collectors.toList());
            if (selected.isEmpty()) { UIUtils.showError("No students selected."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deallocate " + selected.size() + " student(s)?\n"
                + "They will be moved to the Recycle Bin and hidden from active views.",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            Set<Long> ids = selected.stream().map(r -> r.id).collect(Collectors.toSet());
            Task<Void> deallocTask = new Task<>() {
                @Override protected Void call() throws Exception {
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "UPDATE students SET deallocated = 1 WHERE id = ?")) {
                        for (Long id : ids) {
                            ps.setLong(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    return null;
                }
            };
            deallocTask.setOnSucceeded(ev2 -> {
                data.removeIf(r -> ids.contains(r.id));
                table.setItems(data);
                updateStatus(statusLabel, table);
                UIUtils.showInfo("Deallocated " + selected.size() + " student(s).");
            });
            deallocTask.setOnFailed(ev2 -> UIUtils.showError("Error: " + deallocTask.getException().getMessage()));
            new Thread(deallocTask).start();
        });

        HBox paginationBar = new HBox(10, prevBtn, pageLabel, nextBtn);
        paginationBar.setAlignment(Pos.CENTER);

        VBox content = new VBox(10);
        content.getChildren().addAll(header, streamCards, actions, spinner, table, paginationBar);
        view.getChildren().addAll(backBtn, content);
        setView(view);
    }

    private void updateStatus(Label label, TableView<?> table) {
        int total = table.getItems().size();
        long sel = table.getItems().stream().filter(r -> r instanceof StudentRow && ((StudentRow)r).isSelected()).count();
        label.setText(sel + " of " + total + " selected");
    }

    // ───── Row Class ─────

    public static class StudentRow {
        private final long id;
        private final String admission, name, streamName;
        private final javafx.beans.property.SimpleBooleanProperty selected;

        public StudentRow(long id, String admission, String name, String streamName) {
            this.id = id;
            this.admission = admission;
            this.name = name;
            this.streamName = streamName;
            this.selected = new javafx.beans.property.SimpleBooleanProperty(false);
        }

        public long getId() { return id; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public String getStreamName() { return streamName; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean v) { selected.set(v); }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
    }

    // ───── CheckBox TableCell ─────

    private static class CheckBoxTableCell<S> extends TableCell<S, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        CheckBoxTableCell() {
            checkBox.setOnAction(e -> {
                @SuppressWarnings("unchecked")
                StudentRow row = (StudentRow) getTableRow().getItem();
                if (row != null) row.setSelected(checkBox.isSelected());
            });
        }

        @Override protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
            } else {
                StudentRow row = (StudentRow) getTableRow().getItem();
                checkBox.setSelected(row.isSelected());
                setGraphic(checkBox);
            }
        }
    }

    // ───── Subject Assignment Integration ─────

    private void showSubjectAssignment(int form, String stream) {
        SubjectAssignmentForm saf = new SubjectAssignmentForm(db);
        saf.loadForStream(form, stream);

        VBox wrapper = new VBox(10);
        Button backBtn = new Button("← Back to Students");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY + "; -fx-font-size: 13;");
        backBtn.setOnAction(e -> showStudentList(form));
        wrapper.getChildren().addAll(backBtn, saf.getView());
        setView(wrapper);
    }

    // ───── Helpers ─────

    private void styleBackBtn(Button btn) {
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY + "; "
            + "-fx-font-size: 13; -fx-padding: 5 0 5 0;");
    }


}
