package com.zaraki.exams;

import com.zaraki.exams.auth.LoginForm;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.forms.DashboardForm;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    private Stage stage;
    private DashboardForm dashboard;
    private String loggedInUser;
    private String loggedInUsername;
    private String loggedInRole;
    private long loggedInUserId;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Image appIcon = loadAppIcon();
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }
        showLogin();
    }

    private Image loadAppIcon() {
        try {
            return new Image(getClass().getResourceAsStream("/images/school_logo.jpeg"));
        } catch (Exception e) {
            return null;
        }
    }

    private void showLogin() {
        DatabaseEngine db = DatabaseEngine.getInstance();
        LoginForm login = new LoginForm(db, null);
        login.setOnLoginSuccess(() -> {
            loggedInUser = login.getLoggedInUser();
            loggedInUsername = login.getLoggedInUsername();
            loggedInRole = login.getLoggedInRole();
            loggedInUserId = login.getLoggedInUserId();
            dashboard = new DashboardForm(stage, loggedInUser, loggedInUsername, loggedInRole, loggedInUserId, this::showLogin);
            Scene mainScene = new Scene(dashboard.getView(), 1200, 750);
            mainScene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            stage.setTitle("Thorium Exam Analysis System v2");
            stage.setScene(mainScene);
        });
        Scene loginScene = new Scene(login.getView(), 1200, 750);
        stage.setTitle("Thorium Exam Analysis System - Login");
        stage.setScene(loginScene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
