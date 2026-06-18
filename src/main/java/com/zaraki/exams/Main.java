package com.zaraki.exams;

import com.zaraki.exams.forms.DashboardForm;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        DashboardForm dashboard = new DashboardForm(stage);
        Scene scene = new Scene(dashboard.getView(), 1200, 750);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        stage.setTitle("Thorium Exam Analysis System v2");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
