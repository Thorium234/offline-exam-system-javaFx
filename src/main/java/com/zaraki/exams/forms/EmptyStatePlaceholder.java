package com.zaraki.exams.forms;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class EmptyStatePlaceholder {

    private final VBox view;

    public EmptyStatePlaceholder(String message) {
        this(message, "\uD83D\uDCCB");
    }

    public EmptyStatePlaceholder(String message, String iconEmoji) {
        view = new VBox(10);
        view.setAlignment(Pos.CENTER);
        view.setPrefHeight(200);

        Label icon = new Label(iconEmoji);
        icon.setFont(Font.font("System", 40));

        Label msg = new Label(message);
        msg.setFont(Font.font("System", FontWeight.NORMAL, 14));
        msg.setTextFill(Color.gray(0.5));
        msg.setWrapText(true);
        msg.setAlignment(Pos.CENTER);

        view.getChildren().addAll(icon, msg);
    }

    public VBox getView() {
        return view;
    }
}
