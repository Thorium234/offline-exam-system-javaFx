package com.zaraki.exams.auth;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class UserManagementForm {

    private final DatabaseEngine db;
    private TableView<UserRow> table;
    private ObservableList<UserRow> data;
    private Label statusLabel;

    public UserManagementForm() {
        this.db = DatabaseEngine.getInstance();
    }

    public VBox getView() {
        VBox view = new VBox(15);

        Label header = new Label("User Management");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label info = new Label("Create and manage system users. Passwords are hashed and cannot be retrieved.");
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.gray(0.5));

        HBox toolbar = new HBox(10);
        Button addBtn = new Button("+ New User");
        addBtn.setStyle("-fx-background-color: #1a237e; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
        Button refreshBtn = new Button("Refresh");
        toolbar.getChildren().addAll(addBtn, refreshBtn);

        statusLabel = new Label();
        statusLabel.setTextFill(Color.gray(0.5));

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);

        TableColumn<UserRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(50);

        TableColumn<UserRow, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUser.setPrefWidth(150);

        TableColumn<UserRow, String> colName = new TableColumn<>("Full Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colName.setPrefWidth(250);

        TableColumn<UserRow, String> colRole = new TableColumn<>("Role");
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colRole.setPrefWidth(100);

        TableColumn<UserRow, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(160);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(5, editBtn, delBtn);
            {
                editBtn.setStyle("-fx-background-color: #ff8f00; -fx-text-fill: white; -fx-background-radius: 4;");
                delBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-background-radius: 4;");
                pane.setAlignment(Pos.CENTER);
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                UserRow row = getTableView().getItems().get(getIndex());
                editBtn.setOnAction(e -> showUserDialog(row, false));
                delBtn.setOnAction(e -> deleteUser(row));
                setGraphic(pane);
            }
        });

        table.getColumns().addAll(colId, colUser, colName, colRole, colActions);
        data = FXCollections.observableArrayList();
        load();
        table.setItems(data);

        addBtn.setOnAction(e -> showUserDialog(null, true));
        refreshBtn.setOnAction(e -> load());

        view.getChildren().addAll(header, info, toolbar, statusLabel, table);
        return view;
    }

    private void load() {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, full_name, role FROM users ORDER BY username")) {
            while (rs.next())
                data.add(new UserRow(rs.getLong("id"), rs.getString("username"),
                    rs.getString("full_name"), rs.getString("role")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showUserDialog(UserRow existing, boolean isNew) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New User" : "Edit User");
        dialog.setHeaderText(isNew ? "Create a new system user" : "Edit user details");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full Name");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm Password");
        ComboBox<String> roleBox = new ComboBox<>(
            FXCollections.observableArrayList("admin", "teacher"));
        roleBox.setValue("teacher");

        if (!isNew && existing != null) {
            usernameField.setText(existing.getUsername());
            fullNameField.setText(existing.getFullName());
            roleBox.setValue(existing.getRole());
            passwordField.setPromptText("Leave blank to keep current");
            confirmField.setPromptText("Leave blank to keep current");
        }

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Full Name:"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new Label("Confirm:"), 0, 3);
        grid.add(confirmField, 1, 3);
        grid.add(new Label("Role:"), 0, 4);
        grid.add(roleBox, 1, 4);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(usernameField::requestFocus);

        dialog.setResultConverter(dialogBtn -> {
            if (dialogBtn != saveBtn) return null;
            String username = usernameField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();
            String role = roleBox.getValue();

            if (username.isEmpty() || fullName.isEmpty() || role == null) {
                showAlert("Username, full name, and role are required.");
                return null;
            }
            if (isNew && password.isEmpty()) {
                showAlert("Password is required for new users.");
                return null;
            }
            if (!password.isEmpty() && !password.equals(confirm)) {
                showAlert("Passwords do not match.");
                return null;
            }

            try (Connection conn = db.getConnection()) {
                if (isNew) {
                    String salt = PasswordUtils.generateSalt();
                    String hash = PasswordUtils.hashPassword(password, salt);
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)")) {
                        ps.setString(1, username);
                        ps.setString(2, hash);
                        ps.setString(3, salt);
                        ps.setString(4, fullName);
                        ps.setString(5, role);
                        ps.executeUpdate();
                    }
                } else if (existing != null) {
                    if (password.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE users SET username=?, full_name=?, role=? WHERE id=?")) {
                            ps.setString(1, username);
                            ps.setString(2, fullName);
                            ps.setString(3, role);
                            ps.setLong(4, existing.getId());
                            ps.executeUpdate();
                        }
                    } else {
                        String salt = PasswordUtils.generateSalt();
                        String hash = PasswordUtils.hashPassword(password, salt);
                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE users SET username=?, password_hash=?, salt=?, full_name=?, role=? WHERE id=?")) {
                            ps.setString(1, username);
                            ps.setString(2, hash);
                            ps.setString(3, salt);
                            ps.setString(4, fullName);
                            ps.setString(5, role);
                            ps.setLong(6, existing.getId());
                            ps.executeUpdate();
                        }
                    }
                }
                load();
                statusLabel.setText("User saved: " + username);
            } catch (SQLException e) { showAlert(e.getMessage()); }
            return null;
        });

        dialog.showAndWait();
    }

    private void deleteUser(UserRow row) {
        if (row.getRole().equals("admin")) {
            long adminCount = 0;
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role='admin'")) {
                if (rs.next()) adminCount = rs.getLong(1);
            } catch (SQLException ignored) {}
            if (adminCount <= 1) {
                showAlert("Cannot delete the last admin user.");
                return;
            }
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete user '" + row.getUsername() + "'?", ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setLong(1, row.getId());
            ps.executeUpdate();
            load();
            statusLabel.setText("User deleted: " + row.getUsername());
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, msg).showAndWait());
    }

    public static class UserRow {
        private final Long id;
        private final String username;
        private final String fullName;
        private final String role;

        public UserRow(Long id, String username, String fullName, String role) {
            this.id = id; this.username = username; this.fullName = fullName; this.role = role;
        }
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
    }
}
