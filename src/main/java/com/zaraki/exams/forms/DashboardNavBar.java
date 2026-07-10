package com.zaraki.exams.forms;

import com.zaraki.exams.config.CurriculumSystem;
import com.zaraki.exams.config.SettingsManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;
import java.util.function.Consumer;

public class DashboardNavBar {

    private final ComboBox<String> curriculumSwitcher;
    private final HBox view;
    private Node activeNavItem;

    public DashboardNavBar(String role, Consumer<String> onNavigate) {
        SettingsManager settings = new SettingsManager();

        view = new HBox(6);
        view.setMinHeight(64);
        view.setPrefHeight(64);
        view.setAlignment(Pos.CENTER_LEFT);
        view.setPadding(new Insets(0, 20, 0, 20));
        view.getStyleClass().add("navbar");

        curriculumSwitcher = new ComboBox<>();
        curriculumSwitcher.setItems(FXCollections.observableArrayList(
            CurriculumSystem.SYSTEM_844.getDisplayName(),
            CurriculumSystem.CBC.getDisplayName()
        ));
        CurriculumSystem current = settings.getCurriculum();
        curriculumSwitcher.setValue(current.getDisplayName());
        curriculumSwitcher.getStyleClass().addAll("button", "button-secondary");
        curriculumSwitcher.setOnAction(e -> {
            String val = curriculumSwitcher.getValue();
            CurriculumSystem cs = val.equals(CurriculumSystem.CBC.getDisplayName())
                ? CurriculumSystem.CBC : CurriculumSystem.SYSTEM_844;
            settings.setCurriculum(cs);
        });
        curriculumSwitcher.setPrefWidth(140);

        Region divider = new Region();
        divider.setMinWidth(12);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border: none;");
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(false);
        scrollPane.setPrefHeight(64);
        scrollPane.setMinHeight(64);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);

        HBox navItems = new HBox(2);
        navItems.setAlignment(Pos.CENTER_LEFT);
        navItems.setPadding(new Insets(0, 10, 0, 10));

        List<String> itemNames;
        if ("teacher".equals(role)) {
            itemNames = List.of("Dashboard", "Marks Entry", "Bulk Marks");
        } else {
            itemNames = List.of("Dashboard", "Students", "Subjects", "Exams",
                "Grading Scales", "Grading Systems", "Ranking Profiles", "Users", "Teacher Subjects", "Streams",
                "Stream Subjects", "Settings", "Publish", "Marks Entry",
                "Bulk Marks", "Analysis", "Reports", "Browse Students", "Recycle Bin");
        }

        for (String itemName : itemNames) {
            String icon = getIconForNavItem(itemName);
            VBox item = new VBox(0);
            item.setAlignment(Pos.CENTER);
            item.setPadding(new Insets(8, 14, 6, 14));
            item.setMinWidth(64);
            item.getStyleClass().add("navbar-item");

            Label iconLabel = new Label(icon);
            iconLabel.setFont(Font.font("System", 18));
            iconLabel.getStyleClass().add("navbar-icon");

            Label textLabel = new Label(itemName);
            textLabel.setFont(Font.font("System", 10));
            textLabel.setTextFill(Color.gray(0.5));
            textLabel.getStyleClass().add("navbar-text");

            item.getChildren().addAll(iconLabel, textLabel);
            String page = itemName.toLowerCase().replace(" ", "_");

            item.setOnMouseEntered(e -> {
                if (item != activeNavItem) {
                    item.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 6; -fx-cursor: hand;");
                }
            });
            item.setOnMouseExited(e -> {
                if (item != activeNavItem) {
                    item.setStyle("-fx-background-color: transparent; -fx-background-radius: 6; -fx-cursor: hand;");
                }
            });
            item.setOnMouseClicked(e -> {
                onNavigate.accept(page);
                setActiveNavItem(item);
            });
            navItems.getChildren().add(item);
        }

        if (!navItems.getChildren().isEmpty()) {
            VBox first = (VBox) navItems.getChildren().get(0);
            activeNavItem = first;
            first.getStyleClass().add("navbar-item-active");
            ((Label) first.getChildren().get(1)).getStyleClass().add("navbar-text-active");
        }

        scrollPane.setContent(navItems);
        view.getChildren().addAll(curriculumSwitcher, divider, scrollPane);
    }

    public HBox getView() { return view; }
    public ComboBox<String> getCurriculumSwitcher() { return curriculumSwitcher; }

    public void setActiveNavItem(VBox item) {
        if (activeNavItem != null) {
            activeNavItem.getStyleClass().remove("navbar-item-active");
            ((Label) ((VBox) activeNavItem).getChildren().get(1)).getStyleClass().remove("navbar-text-active");
            ((Label) ((VBox) activeNavItem).getChildren().get(1)).setTextFill(Color.gray(0.5));
        }
        activeNavItem = item;
        item.getStyleClass().add("navbar-item-active");
        ((Label) item.getChildren().get(1)).getStyleClass().add("navbar-text-active");
        ((Label) item.getChildren().get(1)).setTextFill(Color.web(AppTheme.PRIMARY));
    }

    private String getIconForNavItem(String item) {
        return switch (item) {
            case "Dashboard" -> AppTheme.SIDEBAR_ICON_DASHBOARD;
            case "Students" -> AppTheme.SIDEBAR_ICON_STUDENTS;
            case "Subjects" -> AppTheme.SIDEBAR_ICON_SUBJECTS;
            case "Exams" -> AppTheme.SIDEBAR_ICON_EXAMS;
            case "Grading Scales" -> AppTheme.SIDEBAR_ICON_GRADES;
            case "Grading Systems" -> AppTheme.SIDEBAR_ICON_GRADES;
            case "Ranking Profiles" -> AppTheme.SIDEBAR_ICON_GRADES;
            case "Users" -> AppTheme.SIDEBAR_ICON_USERS;
            case "Teacher Subjects" -> AppTheme.SIDEBAR_ICON_TEACHERS;
            case "Streams" -> AppTheme.SIDEBAR_ICON_STREAMS;
            case "Stream Subjects" -> AppTheme.SIDEBAR_ICON_STREAMS;
            case "Settings" -> AppTheme.SIDEBAR_ICON_SETTINGS;
            case "Publish" -> AppTheme.SIDEBAR_ICON_PUBLISH;
            case "Marks Entry" -> AppTheme.SIDEBAR_ICON_MARKS;
            case "Bulk Marks" -> AppTheme.SIDEBAR_ICON_BULK;
            case "Analysis" -> AppTheme.SIDEBAR_ICON_ANALYSIS;
            case "Reports" -> AppTheme.SIDEBAR_ICON_REPORTS;
            case "Browse Students" -> AppTheme.SIDEBAR_ICON_BROWSE;
            case "Recycle Bin" -> AppTheme.SIDEBAR_ICON_RECYCLE;
            default -> "  ";
        };
    }
}
