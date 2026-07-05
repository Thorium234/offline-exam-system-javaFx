package com.zaraki.exams.forms;

import com.zaraki.exams.config.SettingsManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.Scene;

public class DashboardTopBar {

    private final String loggedInUser;
    private final boolean darkMode;
    private final Runnable onLogout;
    private final Runnable onToggleDark;
    private Button darkModeToggle;
    private HBox view;

    public DashboardTopBar(String loggedInUser, boolean darkMode, Runnable onLogout, Runnable onToggleDark) {
        this.loggedInUser = loggedInUser;
        this.darkMode = darkMode;
        this.onLogout = onLogout;
        this.onToggleDark = onToggleDark;
        build();
    }

    private void build() {
        view = new HBox();
        view.setMinHeight(56);
        view.setPrefHeight(56);
        view.setAlignment(Pos.CENTER_LEFT);
        view.getStyleClass().add("topbar");

        HBox leftSection = new HBox(10);
        leftSection.setAlignment(Pos.CENTER_LEFT);
        leftSection.setPadding(new Insets(0, 0, 0, 20));

        ImageView logoView = null;
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/school_logo.jpeg"));
            logoView = new ImageView(logo);
            logoView.setFitWidth(28);
            logoView.setFitHeight(28);
            logoView.setPreserveRatio(true);
        } catch (Exception ignored) {}

        Label appTitle = new Label("Exam Analysis Tool");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        appTitle.setTextFill(Color.web(AppTheme.PRIMARY));
        appTitle.getStyleClass().add("topbar-brand");

        if (logoView != null) leftSection.getChildren().add(logoView);
        leftSection.getChildren().add(appTitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rightSection = new HBox(12);
        rightSection.setAlignment(Pos.CENTER_RIGHT);
        rightSection.setPadding(new Insets(0, 20, 0, 0));

        darkModeToggle = new Button(darkMode ? "\u2600\uFE0F  Light" : "\uD83C\uDF19  Dark");
        darkModeToggle.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-font-size: 13; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #e0e0e0; -fx-border-radius: 6; -fx-border-width: 1;");
        darkModeToggle.setOnAction(e -> {
            if (onToggleDark != null) onToggleDark.run();
        });

        Label userBadge = new Label("\uD83D\uDC64 " + loggedInUser);
        userBadge.setFont(Font.font("System", 13));
        userBadge.setTextFill(Color.gray(0.5));
        userBadge.getStyleClass().add("topbar-user");

        Button logoutBtn = new Button("\uD83D\uDEAA  Logout");
        logoutBtn.getStyleClass().add("topbar-logout");
        logoutBtn.setOnAction(e -> { if (onLogout != null) onLogout.run(); });

        rightSection.getChildren().addAll(darkModeToggle, userBadge, logoutBtn);
        view.getChildren().addAll(leftSection, spacer, rightSection);
    }

    public HBox getView() { return view; }

    public void setDarkModeText(boolean dark) {
        darkModeToggle.setText(dark ? "\u2600\uFE0F  Light" : "\uD83C\uDF19  Dark");
    }
}
