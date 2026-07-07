package com.zaraki.exams.util;

import com.zaraki.exams.repository.IExamRepository;
import com.zaraki.exams.repository.IStreamRepository;
import com.zaraki.exams.repository.ExamRepositoryImpl;
import com.zaraki.exams.repository.StreamRepositoryImpl;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class UIUtils {

    private static final IExamRepository examRepo = new ExamRepositoryImpl();
    private static final IStreamRepository streamRepo = new StreamRepositoryImpl();

    private UIUtils() {}

    public static void showError(String msg) {
        LoggerUtil.warn(msg);
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg);
            a.showAndWait();
        });
    }

    public static void showInfo(String msg) {
        LoggerUtil.info(msg);
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
        try {
            var exams = examRepo.findAllDesc();
            for (var e : exams) {
                box.getItems().add(e.get("id") + " - " + e.get("academic_year") + " " + e.get("term") + " " + e.get("exam_series"));
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    public static void loadStreams(ComboBox<String> box) {
        Set<String> names = streamRepo.findAllNames();
        box.getItems().clear();
        box.getItems().addAll(names);
    }

    public static void loadStreamsWithForms(ComboBox<String> box) {
        var streams = streamRepo.findAllWithStudentCount();
        box.getItems().clear();
        for (var s : streams) {
            int form = (Integer) s.get("form");
            String stream = (String) s.get("stream");
            box.getItems().add("Form " + form + " " + stream);
        }
    }

    public static void loadStreamsInto(Set<String> target) {
        target.clear();
        target.addAll(streamRepo.findAllNames());
    }

    public static void loadForms(ComboBox<String> box) {
        box.getItems().clear();
        Set<Integer> forms = streamRepo.findAllDistinctForms();
        for (int f : forms) box.getItems().add(String.valueOf(f));
    }

    public static List<Integer> loadFormsList() {
        List<Integer> forms = new ArrayList<>();
        forms.addAll(streamRepo.findAllDistinctForms());
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
