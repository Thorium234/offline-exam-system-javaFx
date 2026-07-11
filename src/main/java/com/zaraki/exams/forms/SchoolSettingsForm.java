package com.zaraki.exams.forms;

import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseBackupService;
import com.zaraki.exams.util.UIUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;



import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SchoolSettingsForm {

    private final SettingsManager settings;
    private Label statusLabel;

    public SchoolSettingsForm() {
        this.settings = new SettingsManager();
    }

    public VBox getView() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(20));

        Label header = UIUtils.makeHeader("School Settings");

        Label info = new Label("Customise school information for report cards.");

        // ── School Name ──
        Label nameLabel = new Label("School Name");
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        TextField nameField = new TextField(settings.getSchoolName());
        nameField.setPrefWidth(400);

        // ── Dates ──
        Label datesLabel = new Label("Term Dates");
        datesLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        HBox datesRow = new HBox(15);
        TextField openField = new TextField(settings.getOpeningDate());
        openField.setPromptText("Opening Date (e.g. 05/05/2026)");
        openField.setPrefWidth(250);
        TextField closeField = new TextField(settings.getClosingDate());
        closeField.setPromptText("Closing Date (e.g. 09/08/2026)");
        closeField.setPrefWidth(250);
        datesRow.getChildren().addAll(new Label("Opens:"), openField, new Label("Closes:"), closeField);

        // ── Logo ──
        Label logoLabel = new Label("School Logo");
        logoLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        HBox logoRow = new HBox(15);
        ImageView logoPreview = new ImageView();
        logoPreview.setFitWidth(80);
        logoPreview.setFitHeight(80);
        logoPreview.setPreserveRatio(true);
        String curLogo = settings.getLogoPath();
        if (curLogo != null && !curLogo.isBlank()) {
            try {
                logoPreview.setImage(new Image(new java.io.File(curLogo).toURI().toString()));
            } catch (RuntimeException ex) {
                UIUtils.showError("Failed to load logo preview: " + ex.getMessage());
            }
        }
        TextField logoPathField = new TextField(curLogo);
        logoPathField.setPrefWidth(300);
        logoPathField.setEditable(false);
        Button logoBrowseBtn = new Button("Browse...");
        Button logoClearBtn = new Button("Clear");
        logoRow.getChildren().addAll(logoPreview, logoPathField, logoBrowseBtn, logoClearBtn);

        // ── Rubber Stamp ──
        Label stampLabel = new Label("Rubber Stamp");
        stampLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        HBox stampRow = new HBox(15);
        ImageView stampPreview = new ImageView();
        stampPreview.setFitWidth(100);
        stampPreview.setFitHeight(60);
        stampPreview.setPreserveRatio(true);
        String curStamp = settings.getStampPath();
        if (curStamp != null && !curStamp.isBlank()) {
            try {
                stampPreview.setImage(new Image(new java.io.File(curStamp).toURI().toString()));
            } catch (RuntimeException ex) {
                UIUtils.showError("Failed to load stamp preview: " + ex.getMessage());
            }
        }
        TextField stampPathField = new TextField(curStamp);
        stampPathField.setPrefWidth(300);
        stampPathField.setEditable(false);
        Button stampBrowseBtn = new Button("Browse...");
        Button stampClearBtn = new Button("Clear");
        stampRow.getChildren().addAll(stampPreview, stampPathField, stampBrowseBtn, stampClearBtn);

        // ── Best-of-N ──
        Label bestOfNLabel = new Label("Best-of-N Grading");
        bestOfNLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        HBox bestOfNRow = new HBox(10);
        int curBestOfN = 0;
        try { curBestOfN = Integer.parseInt(settings.getSetting("best_of_n", "0")); } catch (NumberFormatException ignored) {}
        Spinner<Integer> bestOfNSpinner = new Spinner<>(0, 7, curBestOfN);
        bestOfNSpinner.setPrefWidth(80);
        bestOfNSpinner.setEditable(true);
        Label bestOfNHelp = new Label("0 = disabled. Picks best N subject scores for total points.");
        bestOfNHelp.setFont(Font.font("System", 11));
        bestOfNHelp.setTextFill(Color.gray(0.5));
        bestOfNRow.getChildren().addAll(bestOfNSpinner, bestOfNHelp);

        // ── Performance Band Remarks ──
        Label remarkLabel = new Label("Performance Band Auto-Remarks");
        remarkLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        VBox remarkRows = new VBox(8);

        HBox highRow = new HBox(10);
        Label highLbl = new Label("High (\u226570%):");
        highLbl.setPrefWidth(100);
        TextField highField = new TextField(settings.getSetting("remark_high", "Excellent performance. Keep it up!"));
        highField.setPrefWidth(500);
        highRow.getChildren().addAll(highLbl, highField);

        HBox avgRow = new HBox(10);
        Label avgLbl = new Label("Average (50-69%):");
        avgLbl.setPrefWidth(100);
        TextField avgField = new TextField(settings.getSetting("remark_average", "Good performance. Room for improvement."));
        avgField.setPrefWidth(500);
        avgRow.getChildren().addAll(avgLbl, avgField);

        HBox lowRow = new HBox(10);
        Label lowLbl = new Label("Low (<50%):");
        lowLbl.setPrefWidth(100);
        TextField lowField = new TextField(settings.getSetting("remark_low", "Needs more effort and focus."));
        lowField.setPrefWidth(500);
        lowRow.getChildren().addAll(lowLbl, lowField);

        remarkRows.getChildren().addAll(highRow, avgRow, lowRow);

        // ── Save ──
        Button saveBtn = new Button("Save Settings");
        saveBtn.getStyleClass().addAll("button", "button-primary");
        saveBtn.setPrefWidth(200);

        Button backupBtn = new Button("Backup Database");
        backupBtn.getStyleClass().addAll("button", "button-danger");
        backupBtn.setPrefWidth(200);

        statusLabel = UIUtils.makeStatusLabel();

        // ── Events ──
        logoBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select School Logo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File f = fc.showOpenDialog(null);
            if (f != null) {
                try {
                    int dotIdx = f.getName().lastIndexOf('.');
                    if (dotIdx < 0) { UIUtils.showError("File has no extension."); return; }
                    String ext = f.getName().substring(dotIdx);
                    if (!ext.matches("\\.(png|jpg|jpeg|gif)")) {
                        UIUtils.showError("Invalid image format: " + ext);
                        return;
                    }
                    File dest = new File(System.getProperty("user.dir"), "school_logo" + ext);
                    Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logoPathField.setText(dest.getAbsolutePath());
                    logoPreview.setImage(new Image(dest.toURI().toString()));
                } catch (Exception ex) { UIUtils.showError("Failed to copy logo: " + ex.getMessage()); }
            }
        });
        logoClearBtn.setOnAction(e -> { logoPathField.setText(""); logoPreview.setImage(null); });

        stampBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Rubber Stamp");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File f = fc.showOpenDialog(null);
            if (f != null) {
                try {
                    int dotIdx = f.getName().lastIndexOf('.');
                    if (dotIdx < 0) { UIUtils.showError("File has no extension."); return; }
                    String ext = f.getName().substring(dotIdx);
                    if (!ext.matches("\\.(png|jpg|jpeg|gif)")) {
                        UIUtils.showError("Invalid image format: " + ext);
                        return;
                    }
                    File dest = new File(System.getProperty("user.dir"), "school_stamp" + ext);
                    Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    stampPathField.setText(dest.getAbsolutePath());
                    stampPreview.setImage(new Image(dest.toURI().toString()));
                } catch (Exception ex) { UIUtils.showError("Failed to copy stamp: " + ex.getMessage()); }
            }
        });
        stampClearBtn.setOnAction(e -> { stampPathField.setText(""); stampPreview.setImage(null); });

        saveBtn.setOnAction(e -> {
            settings.setSchoolName(nameField.getText().trim());
            settings.setOpeningDate(openField.getText().trim());
            settings.setClosingDate(closeField.getText().trim());
            settings.setLogoPath(logoPathField.getText().trim());
            settings.setStampPath(stampPathField.getText().trim());
            settings.setSetting("best_of_n", String.valueOf(bestOfNSpinner.getValue()));
            settings.setSetting("remark_high", highField.getText().trim());
            settings.setSetting("remark_average", avgField.getText().trim());
            settings.setSetting("remark_low", lowField.getText().trim());
            statusLabel.setText("Settings saved.");
            statusLabel.setTextFill(Color.GREEN);
        });

        backupBtn.setOnAction(e -> {
            try {
                String dbPath = System.getProperty("exam.db.path", "");
                if (dbPath.isEmpty() || dbPath.startsWith("jdbc:sqlite::memory:")) {
                    java.nio.file.Path p = java.nio.file.Paths.get(System.getProperty("user.dir"), "exam_analysis.db");
                    if (java.nio.file.Files.exists(p)) dbPath = p.toString();
                    else { UIUtils.showError("Cannot locate database file for backup."); return; }
                } else if (dbPath.startsWith("jdbc:sqlite:")) {
                    dbPath = dbPath.substring("jdbc:sqlite:".length());
                }
                java.nio.file.Path backup = DatabaseBackupService.createBackup(dbPath);
                if (backup != null) {
                    statusLabel.setText("Backup saved: " + backup.getFileName());
                    statusLabel.setTextFill(Color.GREEN);
                    UIUtils.showInfo("Database backup created:\n" + backup.toAbsolutePath());
                } else {
                    UIUtils.showError("Backup failed. Check logs for details.");
                }
            } catch (Exception ex) {
                UIUtils.showError("Backup error: " + ex.getMessage());
            }
        });

        VBox fields = new VBox(12);
        fields.setPadding(new Insets(15));
        fields.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");
        fields.getChildren().addAll(
            nameLabel, nameField,
            datesLabel, datesRow,
            logoLabel, logoRow,
            stampLabel, stampRow,
            bestOfNLabel, bestOfNRow,
            remarkLabel, remarkRows
        );

        view.getChildren().addAll(header, info, fields, saveBtn, backupBtn, statusLabel);
        return view;
    }
}
