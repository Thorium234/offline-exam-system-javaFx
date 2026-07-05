package com.zaraki.exams.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorHandler {

    private static final Logger LOG = LoggerUtil.getLogger();

    private ErrorHandler() {}

    public static void showError(String message) {
        LOG.warning(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
    }

    public static void showError(String message, Throwable throwable) {
        LOG.log(Level.SEVERE, message, throwable);
        Alert alert = new Alert(Alert.AlertType.ERROR, message + "\n\n" + throwable.getMessage());
        alert.showAndWait();
    }

    public static void showWarning(String message) {
        LOG.warning(message);
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.showAndWait();
    }

    public static void showInfo(String message) {
        LOG.info(message);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.showAndWait();
    }

    public static void showInfo(String message, Throwable throwable) {
        LOG.log(Level.INFO, message, throwable);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.showAndWait();
    }

    public static boolean showConfirm(String title, String message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        confirm.setTitle(title);
        Optional<ButtonType> result = confirm.showAndWait();
        return result.orElse(ButtonType.NO) == ButtonType.YES;
    }

    public static void logAndShow(String message, Throwable throwable) {
        LOG.log(Level.SEVERE, message, throwable);
        Alert alert = new Alert(Alert.AlertType.ERROR, message + "\n\n" + throwable.getMessage());
        alert.showAndWait();
    }
}
