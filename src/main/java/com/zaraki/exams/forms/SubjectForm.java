package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class SubjectForm {

    private final DatabaseEngine db;

    public SubjectForm(DatabaseEngine db) {
        this.db = db;
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = new Label("Subjects");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        HBox form = new HBox(10);
        TextField codeField = new TextField(); codeField.setPromptText("Code");
        TextField nameField = new TextField(); nameField.setPromptText("Name");
        TextField deptField = new TextField(); deptField.setPromptText("Department");
        ComboBox<String> grpBox = new ComboBox<>(FXCollections.observableArrayList("Compulsory", "Elective"));
        grpBox.setPromptText("Grouping");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(codeField, nameField, deptField, grpBox, addBtn);

        TableView<SubjectRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<SubjectRow, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(new PropertyValueFactory<>("code")); cCode.setPrefWidth(100);
        TableColumn<SubjectRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("name")); cName.setPrefWidth(250);
        TableColumn<SubjectRow, String> cDept = new TableColumn<>("Department");
        cDept.setCellValueFactory(new PropertyValueFactory<>("dept")); cDept.setPrefWidth(150);
        TableColumn<SubjectRow, String> cGrp = new TableColumn<>("Grouping");
        cGrp.setCellValueFactory(new PropertyValueFactory<>("grouping")); cGrp.setPrefWidth(120);
        table.getColumns().addAll(cCode, cName, cDept, cGrp);
        table.setPrefHeight(400);

        ObservableList<SubjectRow> data = FXCollections.observableArrayList();
        load(data);
        table.setItems(data);

        addBtn.setOnAction(e -> {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            String dept = deptField.getText().trim();
            String grp = grpBox.getValue();
            if (code.isEmpty() || name.isEmpty() || dept.isEmpty()) {
                showAlert("Code, Name, and Department are required.");
                return;
            }
            if (grp == null) {
                showAlert("Select a grouping (Compulsory or Elective).");
                return;
            }
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)")) {
                ps.setString(1, code);
                ps.setString(2, name);
                ps.setString(3, dept);
                ps.setString(4, grp);
                ps.executeUpdate();
                load(data);
                codeField.clear(); nameField.clear(); deptField.clear(); grpBox.setValue(null);
            } catch (Exception ex) { showAlert(ex.getMessage()); }
        });

        view.getChildren().addAll(header, form, table);
        return view;
    }

    private void load(ObservableList<SubjectRow> data) {
        data.clear();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT subject_code, subject_name, department, grouping FROM subjects")) {
            while (rs.next())
                data.add(new SubjectRow(rs.getString("subject_code"), rs.getString("subject_name"),
                    rs.getString("department"), rs.getString("grouping")));
        } catch (SQLException e) { showAlert(e.getMessage()); }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }

    public static class SubjectRow {
        private final String code, name, dept, grouping;
        public SubjectRow(String code, String name, String dept, String grouping) {
            this.code = code; this.name = name; this.dept = dept; this.grouping = grouping;
        }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDept() { return dept; }
        public String getGrouping() { return grouping; }
    }
}
