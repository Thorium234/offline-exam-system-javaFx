package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.model.GradingSystem;
import com.zaraki.exams.model.GradingSystemEntry;
import com.zaraki.exams.repository.GradingSystemRepositoryImpl;
import com.zaraki.exams.repository.IGradingSystemRepository;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GradingSystemForm {

    private final IGradingSystemRepository repo;
    private final TableView<SystemRow> systemTable;
    private final ObservableList<SystemRow> systemData;
    private final TableView<EntryRow> entryTable;
    private final ObservableList<EntryRow> entryData;
    private ComboBox<String> subjectBox;

    private Long selectedSystemId;

    public GradingSystemForm() {
        this.repo = new GradingSystemRepositoryImpl();
        this.systemTable = new TableView<>();
        this.systemData = FXCollections.observableArrayList();
        this.entryTable = new TableView<>();
        this.entryData = FXCollections.observableArrayList();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(0));

        Label header = UIUtils.makeHeader("Grading Systems");
        Label info = new Label("Define named grading systems (e.g. KCSE 8-4-4, CBC). "
            + "Activate one system to use it for analysis. The legacy 'Grading Scales' page still works as a fallback.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #666;");

        // ── Systems section ──────────────────────────────────
        Label systemsLabel = new Label("Grading Systems");
        systemsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        systemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<SystemRow, Long> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id"));
        cId.setPrefWidth(50);
        TableColumn<SystemRow, String> cName = new TableColumn<>("System Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("systemName"));
        cName.setPrefWidth(180);
        TableColumn<SystemRow, String> cDesc = new TableColumn<>("Description");
        cDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        cDesc.setPrefWidth(200);
        TableColumn<SystemRow, String> cActive = new TableColumn<>("Active");
        cActive.setCellValueFactory(new PropertyValueFactory<>("activeDisplay"));
        cActive.setPrefWidth(70);

        TableColumn<SystemRow, Void> colActivate = new TableColumn<>("Status");
        colActivate.setPrefWidth(80);
        colActivate.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Set Active");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #2e7d32; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    SystemRow row = getTableRow().getItem();
                    if (row.isActive()) {
                        btn.setText("Active");
                        btn.setStyle("-fx-font-size: 10; -fx-background-color: #81c784; -fx-text-fill: white;");
                    } else {
                        btn.setText("Set Active");
                        btn.setStyle("-fx-font-size: 10; -fx-background-color: #2e7d32; -fx-text-fill: white;");
                    }
                    btn.setOnAction(e -> activateSystem(row));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<SystemRow, Void> colSysEdit = new TableColumn<>("Edit");
        colSysEdit.setPrefWidth(50);
        colSysEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> editSystem(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<SystemRow, Void> colSysDel = new TableColumn<>("");
        colSysDel.setPrefWidth(50);
        colSysDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Del");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> deleteSystem(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        systemTable.getColumns().addAll(cId, cName, cDesc, cActive, colActivate, colSysEdit, colSysDel);
        systemTable.setPrefHeight(200);
        systemTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                selectedSystemId = sel.getId();
                loadEntries(sel.getId());
            }
        });

        HBox sysBtnRow = new HBox(10);
        Button addSysBtn = new Button("+ New System");
        addSysBtn.getStyleClass().addAll("button", "button-primary");
        addSysBtn.setOnAction(e -> addSystem());
        Button cloneBtn = new Button("Clone Selected");
        cloneBtn.setOnAction(e -> cloneSelectedSystem());
        Button autoGenBtn = new Button("Auto-Generate from Curriculum");
        autoGenBtn.setOnAction(e -> autoGenerateFromCurriculum());
        sysBtnRow.getChildren().addAll(addSysBtn, cloneBtn, autoGenBtn);

        // ── Entries section ──────────────────────────────────
        Label entriesLabel = new Label("Grade Entries");
        entriesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        subjectBox = new ComboBox<>();
        subjectBox.getItems().add("-- Global --");
        subjectBox.setValue("-- Global --");
        loadSubjects();

        TextField minField = new TextField(); minField.setPromptText("Min"); minField.setPrefWidth(70);
        TextField maxField = new TextField(); maxField.setPromptText("Max"); maxField.setPrefWidth(70);
        TextField gradeField = new TextField(); gradeField.setPromptText("Grade"); gradeField.setPrefWidth(70);
        TextField pointsField = new TextField(); pointsField.setPromptText("Points"); pointsField.setPrefWidth(60);
        TextField remarksField = new TextField(); remarksField.setPromptText("Remarks"); remarksField.setPrefWidth(120);

        Button addEntryBtn = new Button("Add Grade");
        addEntryBtn.getStyleClass().addAll("button", "button-primary");
        addEntryBtn.setOnAction(e -> addEntry(minField, maxField, gradeField, pointsField, remarksField));

        HBox entryForm = new HBox(8, subjectBox, minField, maxField, gradeField, pointsField, remarksField, addEntryBtn);
        entryForm.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        entryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<EntryRow, String> eSubj = new TableColumn<>("Subject");
        eSubj.setCellValueFactory(new PropertyValueFactory<>("subject"));
        eSubj.setPrefWidth(120);
        TableColumn<EntryRow, Double> eMin = new TableColumn<>("Min");
        eMin.setCellValueFactory(new PropertyValueFactory<>("minimum"));
        eMin.setPrefWidth(60);
        TableColumn<EntryRow, Double> eMax = new TableColumn<>("Max");
        eMax.setCellValueFactory(new PropertyValueFactory<>("maximum"));
        eMax.setPrefWidth(60);
        TableColumn<EntryRow, String> eGrade = new TableColumn<>("Grade");
        eGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));
        eGrade.setPrefWidth(60);
        TableColumn<EntryRow, Integer> ePoints = new TableColumn<>("Points");
        ePoints.setCellValueFactory(new PropertyValueFactory<>("points"));
        ePoints.setPrefWidth(55);
        TableColumn<EntryRow, String> eRemarks = new TableColumn<>("Remarks");
        eRemarks.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        eRemarks.setPrefWidth(120);

        TableColumn<EntryRow, Void> colEd = new TableColumn<>("Edit");
        colEd.setPrefWidth(50);
        colEd.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> editEntry(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<EntryRow, Void> colDl = new TableColumn<>("");
        colDl.setPrefWidth(50);
        colDl.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Del");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> deleteEntry(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        entryTable.getColumns().addAll(eSubj, eMin, eMax, eGrade, ePoints, eRemarks, colEd, colDl);
        entryTable.setPrefHeight(300);

        loadSystems();

        view.getChildren().addAll(header, info,
            systemsLabel, systemTable, sysBtnRow,
            new Separator(),
            entriesLabel, entryForm, entryTable);

        return view;
    }

    private void loadSubjects() {
        try {
            var subjects = new GradingSystemRepositoryImpl();
            // Reuse the legacy grading repo for subjects list
            var legacyRepo = new com.zaraki.exams.repository.GradingScaleRepositoryImpl();
            var all = legacyRepo.findAllSubjectsForCombo();
            for (var s : all)
                subjectBox.getItems().add(s.get("id") + ":" + s.get("subject_name"));
        } catch (Exception e) { /* ignore */ }
    }

    private void loadSystems() {
        systemData.clear();
        try {
            List<GradingSystem> systems = repo.findAll();
            for (GradingSystem s : systems)
                systemData.add(new SystemRow(s.getId(), s.getSystemName(), s.getDescription(), s.isActive()));
            systemTable.setItems(systemData);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    private void loadEntries(long systemId) {
        entryData.clear();
        try {
            List<java.util.Map<String, Object>> entries = repo.findEntriesWithSubject(systemId);
            for (var e : entries)
                entryData.add(new EntryRow(
                    (Long) e.get("id"),
                    (Long) e.get("system_id"),
                    e.get("subject_id") != null ? (Long) e.get("subject_id") : null,
                    (String) e.get("subject_name"),
                    (Double) e.get("minimum_mark"),
                    (Double) e.get("maximum_mark"),
                    (String) e.get("grade"),
                    (Integer) e.get("points"),
                    (String) e.get("remarks")));
            entryTable.setItems(entryData);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    private void addSystem() {
        Dialog<GradingSystem> dialog = new Dialog<>();
        dialog.setTitle("New Grading System");
        dialog.setHeaderText("Create a new grading system");

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField nameField = new TextField(); nameField.setPromptText("e.g. KCSE 8-4-4");
        TextField descField = new TextField(); descField.setPromptText("Optional description");
        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == createType) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { UIUtils.showError("Name is required."); return null; }
                return new GradingSystem(name, descField.getText().trim(), false);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(sys -> {
            try {
                repo.insertSystem(sys);
                loadSystems();
                UIUtils.showInfo("Grading system created.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void editSystem(SystemRow row) {
        Dialog<GradingSystem> dialog = new Dialog<>();
        dialog.setTitle("Edit Grading System");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField nameField = new TextField(row.getSystemName());
        TextField descField = new TextField(row.getDescription() != null ? row.getDescription() : "");
        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { UIUtils.showError("Name is required."); return null; }
                GradingSystem sys = new GradingSystem(name, descField.getText().trim(), row.isActive());
                sys.setId(row.getId());
                return sys;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(sys -> {
            try {
                repo.updateSystem(sys);
                loadSystems();
                UIUtils.showInfo("Updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteSystem(SystemRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete grading system '" + row.getSystemName() + "' and all its entries?",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            repo.deleteSystem(row.getId());
            if (selectedSystemId != null && selectedSystemId.equals(row.getId())) {
                selectedSystemId = null;
                entryData.clear();
            }
            loadSystems();
            UIUtils.showInfo("Deleted.");
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void activateSystem(SystemRow row) {
        try {
            repo.setActive(row.getId());
            loadSystems();
            UIUtils.showInfo("'" + row.getSystemName() + "' is now the active grading system.");
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void cloneSelectedSystem() {
        if (selectedSystemId == null) {
            UIUtils.showError("Select a system to clone first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("Copy of " + systemTable.getSelectionModel().getSelectedItem().getSystemName());
        dialog.setTitle("Clone Grading System");
        dialog.setHeaderText("Enter a name for the cloned system:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                repo.cloneSystem(selectedSystemId, name);
                loadSystems();
                UIUtils.showInfo("System cloned.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void autoGenerateFromCurriculum() {
        CurriculumSystem curr = CurriculumSystem.SYSTEM_844;
        // Determine which to use from the existing systems
        try {
            TextInputDialog dialog = new TextInputDialog("KCSE 8-4-4 Auto");
            dialog.setTitle("Auto-Generate Grading System");
            dialog.setHeaderText("Creates a new system with preset grades from the 8-4-4 curriculum.\nEnter a name:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                GradingSystem sys = new GradingSystem(name, "Auto-generated from 8-4-4 curriculum", false);
                long newId = repo.insertSystem(sys);
                List<GradingSystemEntry> entries = new ArrayList<>();
                for (CurriculumSystem.PresetGrade pg : curr.getPresetGrades()) {
                    entries.add(new GradingSystemEntry(newId, null,
                        pg.min(), pg.max(), pg.grade(), pg.points(), pg.remarks()));
                }
                repo.insertBatchEntries(newId, entries);
                loadSystems();
                UIUtils.showInfo("Auto-generated " + entries.size() + " grades in '" + name + "'.");
            });
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    // ── Entry CRUD ──────────────────────────────────────────

    private void addEntry(TextField minField, TextField maxField, TextField gradeField,
                          TextField pointsField, TextField remarksField) {
        if (selectedSystemId == null) {
            UIUtils.showError("Select a grading system first.");
            return;
        }
        try {
            String minText = minField.getText().trim();
            String maxText = maxField.getText().trim();
            String grade = gradeField.getText().trim();
            String ptsText = pointsField.getText().trim();
            if (minText.isEmpty() || maxText.isEmpty() || grade.isEmpty() || ptsText.isEmpty()) {
                UIUtils.showError("Min, Max, Grade, and Points are required.");
                return;
            }
            double min = Double.parseDouble(minText);
            double max = Double.parseDouble(maxText);
            int pts = Integer.parseInt(ptsText);
            if (min >= max) { UIUtils.showError("Min must be less than Max."); return; }

            String subj = subjectBox.getValue();
            Long subjectId = (subj == null || subj.equals("-- Global --")) ? null
                : Long.parseLong(subj.split(":")[0]);

            GradingSystemEntry entry = new GradingSystemEntry(selectedSystemId, subjectId,
                min, max, grade, pts, remarksField.getText().trim());
            repo.insertEntry(entry);
            loadEntries(selectedSystemId);
            minField.clear(); maxField.clear(); gradeField.clear(); pointsField.clear(); remarksField.clear();
        } catch (NumberFormatException ex) {
            UIUtils.showError("Invalid numeric values.");
        } catch (Exception ex) {
            UIUtils.showError(ex.getMessage());
        }
    }

    private void editEntry(EntryRow row) {
        Dialog<EntryRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Grade Entry");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField minField = new TextField(String.valueOf(row.getMinimum()));
        TextField maxField = new TextField(String.valueOf(row.getMaximum()));
        TextField gradeField = new TextField(row.getGrade());
        TextField pointsField = new TextField(String.valueOf(row.getPoints()));
        TextField remarksField = new TextField(row.getRemarks() != null ? row.getRemarks() : "");

        grid.add(new Label("Min:"), 0, 0); grid.add(minField, 1, 0);
        grid.add(new Label("Max:"), 0, 1); grid.add(maxField, 1, 1);
        grid.add(new Label("Grade:"), 0, 2); grid.add(gradeField, 1, 2);
        grid.add(new Label("Points:"), 0, 3); grid.add(pointsField, 1, 3);
        grid.add(new Label("Remarks:"), 0, 4); grid.add(remarksField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                try {
                    double min = Double.parseDouble(minField.getText().trim());
                    double max = Double.parseDouble(maxField.getText().trim());
                    int pts = Integer.parseInt(pointsField.getText().trim());
                    if (min >= max) { UIUtils.showError("Min must be less than Max."); return null; }
                    return new EntryRow(row.getId(), row.getSystemId(), row.getSubjectId(),
                        row.getSubject(), min, max, gradeField.getText().trim(), pts, remarksField.getText().trim());
                } catch (NumberFormatException ex) {
                    UIUtils.showError("Invalid numeric values.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                GradingSystemEntry entry = new GradingSystemEntry(
                    result.getSystemId(), result.getSubjectId(),
                    result.getMinimum(), result.getMaximum(),
                    result.getGrade(), result.getPoints(), result.getRemarks());
                entry.setId(result.getId());
                repo.updateEntry(entry);
                loadEntries(result.getSystemId());
                UIUtils.showInfo("Grade updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteEntry(EntryRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete grade " + row.getGrade() + " (" + row.getMinimum() + "-" + row.getMaximum() + ")?",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            repo.deleteEntry(row.getId());
            loadEntries(row.getSystemId());
            UIUtils.showInfo("Deleted.");
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    // ── Row classes ──────────────────────────────────────────

    public static class SystemRow {
        private final Long id;
        private final String systemName;
        private final String description;
        private final boolean active;

        public SystemRow(Long id, String systemName, String description, boolean active) {
            this.id = id; this.systemName = systemName; this.description = description; this.active = active;
        }
        public Long getId() { return id; }
        public String getSystemName() { return systemName; }
        public String getDescription() { return description; }
        public boolean isActive() { return active; }
        public String getActiveDisplay() { return active ? "Yes" : "No"; }
    }

    public static class EntryRow {
        private final Long id, systemId, subjectId;
        private final String subject;
        private final Double minimum, maximum;
        private final String grade;
        private final Integer points;
        private final String remarks;

        public EntryRow(Long id, Long systemId, Long subjectId, String subject,
                        Double min, Double max, String grade, Integer points, String remarks) {
            this.id = id; this.systemId = systemId; this.subjectId = subjectId; this.subject = subject;
            this.minimum = min; this.maximum = max; this.grade = grade; this.points = points; this.remarks = remarks;
        }
        public Long getId() { return id; }
        public Long getSystemId() { return systemId; }
        public Long getSubjectId() { return subjectId; }
        public String getSubject() { return subject; }
        public Double getMinimum() { return minimum; }
        public Double getMaximum() { return maximum; }
        public String getGrade() { return grade; }
        public Integer getPoints() { return points; }
        public String getRemarks() { return remarks; }
    }
}
