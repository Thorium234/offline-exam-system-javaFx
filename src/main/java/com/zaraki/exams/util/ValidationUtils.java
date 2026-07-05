package com.zaraki.exams.util;

import java.util.regex.Pattern;

public class ValidationUtils {

    private static final Pattern ADMISSION_PATTERN =
        Pattern.compile("^[A-Za-z0-9/\\-]+$");

    private ValidationUtils() {}

    public static String requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(fieldName + " must not be empty");
        return value.trim();
    }

    public static double requireInRange(double value, double min, double max, String fieldName) {
        if (value < min || value > max)
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        return value;
    }

    public static int requirePositive(int value, String fieldName) {
        if (value <= 0)
            throw new IllegalArgumentException(fieldName + " must be positive");
        return value;
    }

    public static double requirePositive(double value, String fieldName) {
        if (value <= 0)
            throw new IllegalArgumentException(fieldName + " must be positive");
        return value;
    }

    public static String requireMaxLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max)
            throw new IllegalArgumentException(fieldName + " must not exceed " + max + " characters");
        return value;
    }

    public static String validateAdmissionNumber(String admission) {
        requireNonEmpty(admission, "Admission number");
        if (!ADMISSION_PATTERN.matcher(admission).matches())
            throw new IllegalArgumentException("Admission number contains invalid characters");
        return admission.trim();
    }
}
