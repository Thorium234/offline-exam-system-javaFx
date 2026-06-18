package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;

import java.sql.*;

public class StudentForm {

    private final DatabaseEngine db;

    public StudentForm(DatabaseEngine db) {
        this.db = db;
    }

    public VBox getView() {
        VBox view = new VBox(15);

        Label header = new Label("Students");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField admField = new TextField(); admField.setPromptText("Admission No.");
        TextField nameField = new TextField(); nameField.setPromptText("Full Name");
        TextField formField = new TextField(); formField.setPromptText("Form");
        TextField streamField = new TextField(); streamField.setPromptText("Stream");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(admField, nameField, formField, streamField, addBtn);

        TableView<StudentRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<StudentRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id")); colId.setPrefWidth(60);
        TableColumn<StudentRow, String> colAdm = new TableColumn<>("Admission");
        colAdm.setCellValueFactory(new PropertyValueFactory<>("admission")); colAdm.setPrefWidth(140);
        TableColumn<StudentRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("name")); colName.setPrefWidth(250);
        TableColumn<StudentRow, Integer> colForm = new TableColumn<>("Form");
        colForm.setCellValueFactory(new PropertyValueFactory<>("form")); colForm.setPrefWidth(70);
        TableColumn<StudentRow, String> colStream = new TableColumn<>("Stream");
        colStream.setCellValueFactory(new PropertyValueFactory<>("stream")); colStream.setPrefWidth(120);
        table.getColumns().addAll(colId, colAdm, colName, colForm, colStream);
        table.setPrefHeight(400);

        ObservableList<StudentRow> data = FXCollections.observableArrayList();
        load(data);
        table.setItems(data);

        addBtn.setOnAction(e -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)")) {
                ps.setString(1, admField.getText());
                ps.setString(2, nameField.getText());
                ps.setInt(3, Integer.parseInt(formField.getText()));
                ps.setString(4, streamField.getText());
                ps.executeUpdate();
                load(data);
                admField.clear(); nameField.clear(); formField.clear(); streamField.clear();
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load(ObservableList<StudentRow> data) {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name, form, stream FROM students")) {
            while (rs.next())
                data.add(new StudentRow(rs.getLong("id"), rs.getString("admission_number"),
                    rs.getString("full_name"), rs.getInt("form"), rs.getString("stream")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public static class StudentRow {
        private final Long id; private final String admission, name; private final Integer form; private final String stream;
        public StudentRow(Long id, String admission, String name, Integer form, String stream) {
            this.id = id; this.admission = admission; this.name = name; this.form = form; this.stream = stream;
        }
        public Long getId() { return id; }
        public String getAdmission() { return admission; }
        public String getName() { return name; }
        public Integer getForm() { return form; }
        public String getStream() { return stream; }
    }
}
