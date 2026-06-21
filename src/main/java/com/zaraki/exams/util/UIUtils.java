package com.zaraki.exams.util;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class UIUtils {

    private static final DatabaseEngine db = DatabaseEngine.getInstance();

    private UIUtils() {}

    public static void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg);
            a.showAndWait();
        });
    }

    public static void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
            a.showAndWait();
        });
    }

    public static Label makeHeader(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 18));
        label.setTextFill(Color.web("#1a237e"));
        return label;
    }

    public static Label makeStatusLabel() {
        Label label = new Label();
        label.setFont(Font.font("System", 12));
        label.setTextFill(Color.gray(0.5));
        return label;
    }

    public static void loadExams(ComboBox<String> box) {
        box.getItems().clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams ORDER BY id DESC")) {
            while (rs.next()) {
                box.getItems().add(rs.getLong("id") + " - " + rs.getString("academic_year") + " " + rs.getString("term") + " " + rs.getString("exam_series"));
            }
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    public static void loadStreams(ComboBox<String> box) {
        box.getItems().clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) box.getItems().add(rs.getString("stream"));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    public static void loadStreamsInto(Set<String> target) {
        target.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT stream FROM streams ORDER BY stream")) {
            while (rs.next()) target.add(rs.getString("stream"));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    public static void loadForms(ComboBox<String> box) {
        box.getItems().clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM streams ORDER BY form")) {
            while (rs.next()) box.getItems().add(rs.getString("form"));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    public static List<Integer> loadFormsList() {
        List<Integer> forms = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT form FROM streams ORDER BY form")) {
            while (rs.next()) forms.add(rs.getInt("form"));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
        return forms;
    }

    public static <T> TableColumn<T, ?> col(String title, String prop, int width) {
        TableColumn<T, ?> c = new TableColumn<>(title);
        c.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    public static <T> TableColumn<T, String> textCol(String title, String prop, int width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    public static Label makeNavLabel(String text, boolean active) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 13));
        label.setTextFill(Color.WHITE);
        label.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));
        label.setMaxWidth(Double.MAX_VALUE);
        if (active) label.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-background-radius: 4;");
        else label.setStyle("-fx-background-radius: 4;");
        label.setOnMouseEntered(e -> {
            if (!active) label.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 4;");
        });
        label.setOnMouseExited(e -> {
            if (!active) label.setStyle("-fx-background-radius: 4;");
        });
        return label;
    }
}
