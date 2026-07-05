package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class DashboardStatCards {

    private final DatabaseEngine db;
    private final HBox view;
    private final ProgressIndicator spinner;
    private final VBox[] cardBoxes = new VBox[4];
    private Runnable onStudentsClick;
    private Runnable onSubjectsClick;
    private Runnable onExamsClick;
    private Runnable onMarksClick;

    public DashboardStatCards(DatabaseEngine db) {
        this.db = db;
        this.spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        view = new HBox(20);
        String[][] cardDefs = {
            {"Students", "\u2026", "\uD83D\uDC65"},
            {"Subjects", "\u2026", "\uD83D\uDCDA"},
            {"Exams", "\u2026", "\uD83D\uDCCB"},
            {"Marks Entered", "\u2026", "\u270F\uFE0F"}
        };

        for (int i = 0; i < 4; i++) {
            cardBoxes[i] = statCard(cardDefs[i][0], cardDefs[i][1], cardDefs[i][2]);
        }
        view.getChildren().addAll(cardBoxes);
        view.getChildren().add(spinner);

        cardBoxes[0].setOnMouseClicked(e -> { if (onStudentsClick != null) onStudentsClick.run(); });
        cardBoxes[0].setCursor(Cursor.HAND);
        cardBoxes[1].setOnMouseClicked(e -> { if (onSubjectsClick != null) onSubjectsClick.run(); });
        cardBoxes[1].setCursor(Cursor.HAND);
        cardBoxes[2].setOnMouseClicked(e -> { if (onExamsClick != null) onExamsClick.run(); });
        cardBoxes[2].setCursor(Cursor.HAND);
        cardBoxes[3].setOnMouseClicked(e -> { if (onMarksClick != null) onMarksClick.run(); });
        cardBoxes[3].setCursor(Cursor.HAND);
    }

    public HBox getView() { return view; }

    public void setOnStudentsClick(Runnable r) { this.onStudentsClick = r; }
    public void setOnSubjectsClick(Runnable r) { this.onSubjectsClick = r; }
    public void setOnExamsClick(Runnable r) { this.onExamsClick = r; }
    public void setOnMarksClick(Runnable r) { this.onMarksClick = r; }

    public void load() {
        spinner.setVisible(true);
        Task<int[]> task = new Task<>() {
            @Override protected int[] call() {
                return new int[]{ count("students"), count("subjects"), count("exams"), count("marks") };
            }
        };
        task.setOnSucceeded(ev -> {
            int[] counts = task.getValue();
            for (int i = 0; i < 4; i++) {
                Label valLabel = (Label) cardBoxes[i].getChildren().get(1);
                valLabel.setText(String.valueOf(counts[i]));
            }
            spinner.setVisible(false);
        });
        task.setOnFailed(ev -> spinner.setVisible(false));
        new Thread(task).start();
    }

    private VBox statCard(String title, String value, String icon) {
        VBox card = new VBox(5);
        card.setPrefSize(210, 110);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("stat-card");
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("System", 20));
        iconLabel.getStyleClass().add("stat-card-icon");
        Label val = new Label(value);
        val.setFont(Font.font("System", FontWeight.BOLD, 28));
        val.setTextFill(Color.web(AppTheme.PRIMARY));
        val.getStyleClass().add("stat-card-value");
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", 12));
        lbl.setTextFill(Color.gray(0.5));
        lbl.getStyleClass().add("stat-card-label");
        card.getChildren().addAll(iconLabel, val, lbl);

        String normalStyle = "-fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);";
        String hoverStyle = "-fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);";
        card.setOnMouseEntered(e -> card.setStyle(hoverStyle + "-fx-background-color: #e8eaf6;"));
        card.setOnMouseExited(e -> card.setStyle(normalStyle + "-fx-background-color: white;"));
        return card;
    }

    private int count(String table) {
        Map<String, String> allowed = Map.of(
            "students", "students", "subjects", "subjects",
            "exams", "exams", "marks", "marks"
        );
        String actualTable = allowed.get(table);
        if (actualTable == null) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + actualTable);
             ResultSet rs = ps.executeQuery()) {
            return rs.getInt(1);
        } catch (SQLException e) { return 0; }
    }
}
