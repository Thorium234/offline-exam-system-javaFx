package com.zaraki.exams.forms;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.ISubjectRepository;
import com.zaraki.exams.repository.SubjectRepositoryImpl;
import com.zaraki.exams.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class SubjectForm {

    private final ISubjectRepository subjectRepo;
    private final TableView<SubjectRow> table = new TableView<>();
    private final ObservableList<SubjectRow> data = FXCollections.observableArrayList();

    public SubjectForm(DatabaseEngine db) {
        this.subjectRepo = new SubjectRepositoryImpl();
    }

    public VBox getView() {
        VBox view = new VBox(15);
        Label header = UIUtils.makeHeader("Subjects");

        Label info = new Label("Add new subjects or delete existing ones. Subjects with existing marks or assignments cannot be deleted.");

        HBox form = new HBox(10);
        TextField codeField = new TextField(); codeField.setPromptText("Code");
        TextField nameField = new TextField(); nameField.setPromptText("Name");
        TextField deptField = new TextField(); deptField.setPromptText("Department");
        ComboBox<String> grpBox = new ComboBox<>(FXCollections.observableArrayList("Compulsory", "Elective"));
        grpBox.setPromptText("Grouping");
        Button addBtn = new Button("Add");
        form.getChildren().addAll(codeField, nameField, deptField, grpBox, addBtn);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<SubjectRow, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(new PropertyValueFactory<>("code")); cCode.setPrefWidth(100);
        TableColumn<SubjectRow, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(new PropertyValueFactory<>("name")); cName.setPrefWidth(250);
        TableColumn<SubjectRow, String> cDept = new TableColumn<>("Department");
        cDept.setCellValueFactory(new PropertyValueFactory<>("dept")); cDept.setPrefWidth(150);
        TableColumn<SubjectRow, String> cGrp = new TableColumn<>("Grouping");
        cGrp.setCellValueFactory(new PropertyValueFactory<>("grouping")); cGrp.setPrefWidth(120);
        TableColumn<SubjectRow, Void> cDel = new TableColumn<>("");
        cDel.setPrefWidth(60);
        cDel.setCellFactory(col -> new TableCell<>() {
            private final Button delBtn = new Button("Delete");
            { delBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-size: 10;"); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    SubjectRow row = (SubjectRow) getTableRow().getItem();
                    delBtn.setOnAction(e -> deleteSubject(row));
                    setGraphic(delBtn);
                }
            }
        });
        table.getColumns().addAll(cCode, cName, cDept, cGrp, cDel);
        table.setPrefHeight(400);

        load();
        table.setItems(data);

        addBtn.setOnAction(e -> {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            String dept = deptField.getText().trim();
            String grp = grpBox.getValue();
            if (code.isEmpty() || name.isEmpty() || dept.isEmpty()) {
                UIUtils.showError("Code, Name, and Department are required.");
                return;
            }
            if (grp == null) {
                UIUtils.showError("Select a grouping (Compulsory or Elective).");
                return;
            }
            try {
                subjectRepo.insert(code, name, dept, grp);
                load();
                codeField.clear(); nameField.clear(); deptField.clear(); grpBox.setValue(null);
            } catch (Exception ex) { UIUtils.showError(ex.getMessage()); }
        });

        view.getChildren().addAll(header, info, form, table);
        return view;
    }

    private void deleteSubject(SubjectRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete subject '" + row.code + " - " + row.name + "'?",
            ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            subjectRepo.deleteByCode(row.code);
            load();
            UIUtils.showInfo("Deleted " + row.code);
        } catch (Exception ex) {
            UIUtils.showError("Cannot delete: " + ex.getMessage());
        }
    }

    private void load() {
        data.clear();
        try {
            var subjects = subjectRepo.findAll();
            for (var s : subjects)
                data.add(new SubjectRow(
                    (String) s.get("subject_code"),
                    (String) s.get("subject_name"),
                    (String) s.get("department"),
                    (String) s.get("grouping")));
            if (data.isEmpty()) {
                table.setPlaceholder(new EmptyStatePlaceholder("No subjects defined yet. Add a subject above.", "\uD83D\uDCDA").getView());
            }
        } catch (Exception e) { UIUtils.showError(e.getMessage()); }
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
