package com.zaraki.exams.forms;

import com.zaraki.exams.model.RankingProfile;
import com.zaraki.exams.model.RankingProfileWeight;
import com.zaraki.exams.repository.IRankingProfileRepository;
import com.zaraki.exams.repository.RankingProfileRepositoryImpl;
import com.zaraki.exams.repository.GradingScaleRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.*;

public class RankingProfileForm {

    private final IRankingProfileRepository repo;
    private final TableView<ProfileRow> profileTable;
    private final ObservableList<ProfileRow> profileData;
    private final TableView<WeightRow> weightTable;
    private final ObservableList<WeightRow> weightData;
    private final ComboBox<String> subjectBox;

    private Long selectedProfileId;

    public RankingProfileForm() {
        this.repo = new RankingProfileRepositoryImpl();
        this.profileTable = new TableView<>();
        this.profileData = FXCollections.observableArrayList();
        this.weightTable = new TableView<>();
        this.weightData = FXCollections.observableArrayList();
        this.subjectBox = new ComboBox<>();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        view.setPadding(new Insets(0));

        Label header = UIUtils.makeHeader("Ranking Profiles");
        Label info = new Label("Configure how students are ranked. Choose a method: "
            + "Total Points (standard), Weighted Subjects (custom multipliers), or Best-of-N (top N subjects only). "
            + "Activate one profile to use it in Analysis.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #666;");

        // ── Profiles section ─────────────────────────────────
        Label profilesLabel = new Label("Ranking Profiles");
        profilesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        profileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ProfileRow, Long> pId = new TableColumn<>("ID");
        pId.setCellValueFactory(new PropertyValueFactory<>("id"));
        pId.setPrefWidth(50);
        TableColumn<ProfileRow, String> pName = new TableColumn<>("Profile Name");
        pName.setCellValueFactory(new PropertyValueFactory<>("profileName"));
        pName.setPrefWidth(160);
        TableColumn<ProfileRow, String> pMethod = new TableColumn<>("Method");
        pMethod.setCellValueFactory(new PropertyValueFactory<>("rankingMethodDisplay"));
        pMethod.setPrefWidth(140);
        TableColumn<ProfileRow, Integer> pBestN = new TableColumn<>("Best-of-N");
        pBestN.setCellValueFactory(new PropertyValueFactory<>("bestOfN"));
        pBestN.setPrefWidth(70);
        TableColumn<ProfileRow, String> pActive = new TableColumn<>("Active");
        pActive.setCellValueFactory(new PropertyValueFactory<>("activeDisplay"));
        pActive.setPrefWidth(60);

        TableColumn<ProfileRow, Void> colActivate = new TableColumn<>("Status");
        colActivate.setPrefWidth(80);
        colActivate.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Set Active");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #2e7d32; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ProfileRow row = getTableRow().getItem();
                    if (row.isActive()) {
                        btn.setText("Active");
                        btn.setStyle("-fx-font-size: 10; -fx-background-color: #81c784; -fx-text-fill: white;");
                    } else {
                        btn.setText("Set Active");
                        btn.setStyle("-fx-font-size: 10; -fx-background-color: #2e7d32; -fx-text-fill: white;");
                    }
                    btn.setOnAction(e -> activateProfile(row));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<ProfileRow, Void> colPEdit = new TableColumn<>("Edit");
        colPEdit.setPrefWidth(50);
        colPEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> editProfile(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<ProfileRow, Void> colPDel = new TableColumn<>("");
        colPDel.setPrefWidth(50);
        colPDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Del");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> deleteProfile(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        profileTable.getColumns().addAll(pId, pName, pMethod, pBestN, pActive, colActivate, colPEdit, colPDel);
        profileTable.setPrefHeight(200);
        profileTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                selectedProfileId = sel.getId();
                loadWeights(sel.getId());
            }
        });

        HBox profBtnRow = new HBox(10);
        Button addProfBtn = new Button("+ New Profile");
        addProfBtn.getStyleClass().addAll("button", "button-primary");
        addProfBtn.setOnAction(e -> addProfile());
        profBtnRow.getChildren().add(addProfBtn);

        // ── Weights section ──────────────────────────────────
        Label weightsLabel = new Label("Subject Weights (for Weighted Subjects method)");
        weightsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        subjectBox.getItems().clear();
        subjectBox.getItems().add("-- Select Subject --");
        subjectBox.setValue("-- Select Subject --");
        loadSubjects();

        TextField weightField = new TextField(); weightField.setPromptText("Weight (e.g. 1.5)"); weightField.setPrefWidth(100);

        Button addWeightBtn = new Button("Add Weight");
        addWeightBtn.getStyleClass().addAll("button", "button-primary");
        addWeightBtn.setOnAction(e -> addWeight(weightField));

        HBox weightForm = new HBox(8, subjectBox, weightField, addWeightBtn);
        weightForm.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        weightTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<WeightRow, String> wSubj = new TableColumn<>("Subject");
        wSubj.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
        wSubj.setPrefWidth(200);
        TableColumn<WeightRow, Double> wWeight = new TableColumn<>("Weight");
        wWeight.setCellValueFactory(new PropertyValueFactory<>("weight"));
        wWeight.setPrefWidth(100);

        TableColumn<WeightRow, Void> colWEdit = new TableColumn<>("Edit");
        colWEdit.setPrefWidth(60);
        colWEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #1565c0; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> editWeight(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        TableColumn<WeightRow, Void> colWDel = new TableColumn<>("");
        colWDel.setPrefWidth(60);
        colWDel.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Del");
            { btn.setStyle("-fx-font-size: 10; -fx-background-color: #c62828; -fx-text-fill: white;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> deleteWeight(getTableRow().getItem()));
                    setGraphic(btn);
                }
            }
        });

        weightTable.getColumns().addAll(wSubj, wWeight, colWEdit, colWDel);
        weightTable.setPrefHeight(250);

        Label hint = new Label("Tip: Weight 1.0 = standard. Weight 2.0 = double importance. "
            + "Subjects without a weight entry use 1.0 by default.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        loadProfiles();

        view.getChildren().addAll(header, info,
            profilesLabel, profileTable, profBtnRow,
            new Separator(),
            weightsLabel, weightForm, weightTable, hint);

        return view;
    }

    private void loadSubjects() {
        try {
            var legacyRepo = new GradingScaleRepositoryImpl();
            var all = legacyRepo.findAllSubjectsForCombo();
            for (var s : all)
                subjectBox.getItems().add(s.get("id") + ":" + s.get("subject_name"));
        } catch (Exception e) { /* ignore */ }
    }

    private void loadProfiles() {
        profileData.clear();
        try {
            List<RankingProfile> profiles = repo.findAll();
            for (RankingProfile p : profiles)
                profileData.add(new ProfileRow(p.getId(), p.getProfileName(), p.getDescription(),
                    p.getRankingMethod(), p.getBestOfN(), p.isActive()));
            profileTable.setItems(profileData);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    private void loadWeights(long profileId) {
        weightData.clear();
        try {
            List<Map<String, Object>> weights = repo.findWeightsWithSubject(profileId);
            for (var w : weights)
                weightData.add(new WeightRow(
                    (Long) w.get("id"),
                    (Long) w.get("profile_id"),
                    (Long) w.get("subject_id"),
                    (String) w.get("subject_name"),
                    (Double) w.get("weight")));
            weightTable.setItems(weightData);
        } catch (Exception e) {
            UIUtils.showError(e.getMessage());
        }
    }

    private void addProfile() {
        Dialog<RankingProfile> dialog = new Dialog<>();
        dialog.setTitle("New Ranking Profile");

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField nameField = new TextField(); nameField.setPromptText("e.g. Top Students Overall");
        TextField descField = new TextField(); descField.setPromptText("Optional description");
        ComboBox<String> methodBox = new ComboBox<>();
        methodBox.getItems().addAll("Total Points", "Weighted Subjects", "Best-of-N");
        methodBox.setValue("Total Points");
        TextField bestOfNField = new TextField("0"); bestOfNField.setPromptText("N (0 = all)");
        bestOfNField.setPrefWidth(80);

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Method:"), 0, 2); grid.add(methodBox, 1, 2);
        grid.add(new Label("Best-of-N:"), 0, 3); grid.add(bestOfNField, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == createType) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { UIUtils.showError("Name is required."); return null; }
                String method = methodToCode(methodBox.getValue());
                int bestOfN = 0;
                try { bestOfN = Integer.parseInt(bestOfNField.getText().trim()); } catch (NumberFormatException ignored) {}
                return new RankingProfile(name, descField.getText().trim(), method, bestOfN, false);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(profile -> {
            try {
                repo.insertProfile(profile);
                loadProfiles();
                UIUtils.showInfo("Profile created.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void editProfile(ProfileRow row) {
        Dialog<RankingProfile> dialog = new Dialog<>();
        dialog.setTitle("Edit Ranking Profile");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField nameField = new TextField(row.getProfileName());
        TextField descField = new TextField(row.getDescription() != null ? row.getDescription() : "");
        ComboBox<String> methodBox = new ComboBox<>();
        methodBox.getItems().addAll("Total Points", "Weighted Subjects", "Best-of-N");
        methodBox.setValue(methodToDisplay(row.getRankingMethod()));
        TextField bestOfNField = new TextField(String.valueOf(row.getBestOfN()));
        bestOfNField.setPrefWidth(80);

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Method:"), 0, 2); grid.add(methodBox, 1, 2);
        grid.add(new Label("Best-of-N:"), 0, 3); grid.add(bestOfNField, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { UIUtils.showError("Name is required."); return null; }
                String method = methodToCode(methodBox.getValue());
                int bestOfN = 0;
                try { bestOfN = Integer.parseInt(bestOfNField.getText().trim()); } catch (NumberFormatException ignored) {}
                RankingProfile p = new RankingProfile(name, descField.getText().trim(), method, bestOfN, row.isActive());
                p.setId(row.getId());
                return p;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(profile -> {
            try {
                repo.updateProfile(profile);
                loadProfiles();
                UIUtils.showInfo("Updated.");
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });
    }

    private void deleteProfile(ProfileRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete ranking profile '" + row.getProfileName() + "'?",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            repo.deleteProfile(row.getId());
            if (selectedProfileId != null && selectedProfileId.equals(row.getId())) {
                selectedProfileId = null;
                weightData.clear();
            }
            loadProfiles();
            UIUtils.showInfo("Deleted.");
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void activateProfile(ProfileRow row) {
        try {
            repo.setActive(row.getId());
            loadProfiles();
            UIUtils.showInfo("'" + row.getProfileName() + "' is now the active ranking profile.");
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private void addWeight(TextField weightField) {
        if (selectedProfileId == null) {
            UIUtils.showError("Select a ranking profile first.");
            return;
        }
        String subj = subjectBox.getValue();
        if (subj == null || subj.equals("-- Select Subject --")) {
            UIUtils.showError("Select a subject.");
            return;
        }
        try {
            long subjectId = Long.parseLong(subj.split(":")[0]);
            double weight = Double.parseDouble(weightField.getText().trim());
            if (weight <= 0) { UIUtils.showError("Weight must be positive."); return; }

            RankingProfileWeight w = new RankingProfileWeight(selectedProfileId, subjectId, weight);
            repo.insertWeight(w);
            loadWeights(selectedProfileId);
            weightField.clear();
            subjectBox.setValue("-- Select Subject --");
        } catch (NumberFormatException ex) {
            UIUtils.showError("Invalid weight value.");
        } catch (Exception ex) {
            UIUtils.showError(ex.getMessage());
        }
    }

    private void editWeight(WeightRow row) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(row.getWeight()));
        dialog.setTitle("Edit Weight");
        dialog.setHeaderText("Weight for " + row.getSubjectName());
        dialog.setContentText("Weight:");
        dialog.showAndWait().ifPresent(val -> {
            try {
                double weight = Double.parseDouble(val.trim());
                if (weight <= 0) { UIUtils.showError("Weight must be positive."); return; }
                RankingProfileWeight w = new RankingProfileWeight(row.getProfileId(), row.getSubjectId(), weight);
                w.setId(row.getId());
                repo.updateWeight(w);
                loadWeights(row.getProfileId());
            } catch (NumberFormatException ex) {
                UIUtils.showError("Invalid weight value.");
            } catch (Exception ex) {
                UIUtils.showError(ex.getMessage());
            }
        });
    }

    private void deleteWeight(WeightRow row) {
        try {
            repo.deleteWeight(row.getId());
            loadWeights(row.getProfileId());
        } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
    }

    private String methodToCode(String display) {
        return switch (display) {
            case "Weighted Subjects" -> RankingProfile.METHOD_WEIGHTED_SUBJECTS;
            case "Best-of-N" -> RankingProfile.METHOD_BEST_OF_N;
            default -> RankingProfile.METHOD_TOTAL_POINTS;
        };
    }

    private String methodToDisplay(String code) {
        return switch (code) {
            case "WEIGHTED_SUBJECTS" -> "Weighted Subjects";
            case "BEST_OF_N" -> "Best-of-N";
            default -> "Total Points";
        };
    }

    // ── Row classes ──────────────────────────────────────────

    public static class ProfileRow {
        private final Long id;
        private final String profileName;
        private final String description;
        private final String rankingMethod;
        private final int bestOfN;
        private final boolean active;

        public ProfileRow(Long id, String profileName, String description, String rankingMethod, int bestOfN, boolean active) {
            this.id = id; this.profileName = profileName; this.description = description;
            this.rankingMethod = rankingMethod; this.bestOfN = bestOfN; this.active = active;
        }
        public Long getId() { return id; }
        public String getProfileName() { return profileName; }
        public String getDescription() { return description; }
        public String getRankingMethod() { return rankingMethod; }
        public int getBestOfN() { return bestOfN; }
        public boolean isActive() { return active; }
        public String getActiveDisplay() { return active ? "Yes" : "No"; }
        public String getRankingMethodDisplay() {
            return switch (rankingMethod) {
                case "WEIGHTED_SUBJECTS" -> "Weighted Subjects";
                case "BEST_OF_N" -> "Best-of-N";
                default -> "Total Points";
            };
        }
    }

    public static class WeightRow {
        private final Long id, profileId, subjectId;
        private final String subjectName;
        private final Double weight;

        public WeightRow(Long id, Long profileId, Long subjectId, String subjectName, Double weight) {
            this.id = id; this.profileId = profileId; this.subjectId = subjectId;
            this.subjectName = subjectName; this.weight = weight;
        }
        public Long getId() { return id; }
        public Long getProfileId() { return profileId; }
        public Long getSubjectId() { return subjectId; }
        public String getSubjectName() { return subjectName; }
        public Double getWeight() { return weight; }
    }
}
