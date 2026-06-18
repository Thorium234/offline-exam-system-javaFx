package com.zaraki.exams;

import com.zaraki.exams.auth.LoginForm;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.forms.DashboardForm;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private Stage stage;
    private DashboardForm dashboard;
    private String loggedInUser;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        showLogin();
    }

    private void showLogin() {
        DatabaseEngine db = DatabaseEngine.getInstance();
        LoginForm login = new LoginForm(db, null);
        login.setOnLoginSuccess(() -> {
            loggedInUser = login.getLoggedInUser();
            dashboard = new DashboardForm(stage, loggedInUser, this::showLogin);
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
