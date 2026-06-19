package com.zaraki.exams.auth;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class LoginForm {

    private final DatabaseEngine db;
    private Runnable onLoginSuccess;

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private String loggedInUser = "";
    private String loggedInUsername = "";
    private String loggedInRole = "";

    public LoginForm(DatabaseEngine db, Runnable onLoginSuccess) {
        this.db = db;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void setOnLoginSuccess(Runnable onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    public VBox getView() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #1a237e;");

        VBox card = new VBox(15);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.setPadding(new Insets(40, 35, 40, 35));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");

        Label title = new Label("THORIUM");
        title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(Color.web("#1a237e"));

        Label subtitle = new Label("Exam Analysis System");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.gray(0.5));

        Label loginLabel = new Label("Sign In");
        loginLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        loginLabel.setTextFill(Color.DARKGRAY);

        usernameField.setPromptText("Username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 14; -fx-background-radius: 6; -fx-border-color: #ddd; -fx-border-radius: 6;");

        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-font-size: 14; -fx-background-radius: 6; -fx-border-color: #ddd; -fx-border-radius: 6;");

        errorLabel.setTextFill(Color.RED);
        errorLabel.setFont(Font.font("System", 12));

        Button loginBtn = new Button("Login");
        loginBtn.setPrefHeight(40);
        loginBtn.setPrefWidth(200);
        loginBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-background-radius: 6;");
        loginBtn.setOnAction(e -> attemptLogin());

        passwordField.setOnAction(e -> attemptLogin());

        VBox separator = new VBox();
        separator.setPrefHeight(10);

        card.getChildren().addAll(title, subtitle, separator, loginLabel, usernameField, passwordField, errorLabel, loginBtn);
        root.getChildren().add(card);
        return root;
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Enter both username and password.");
            return;
        }

        String sql = "SELECT password_hash, salt, full_name, role FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("password_hash");
                String salt = rs.getString("salt");
                if (PasswordUtils.verify(password, salt, hash)) {
                    loggedInUser = rs.getString("full_name");
                    loggedInUsername = username;
                    loggedInRole = rs.getString("role");
                    if (onLoginSuccess != null) onLoginSuccess.run();
                } else {
                    errorLabel.setText("Invalid password.");
                }
            } else {
                errorLabel.setText("User not found.");
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    public String getLoggedInUser() { return loggedInUser; }
    public String getLoggedInUsername() { return loggedInUsername; }
    public String getLoggedInRole() { return loggedInRole; }
}
