package com.zaraki.exams.config;

import java.util.List;

public enum CurriculumSystem {

    SYSTEM_844("8-4-4 System"),
    CBC("Competency Based Curriculum (CBC)");

    private final String displayName;

    CurriculumSystem(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CurriculumSystem fromCode(String code) {
        for (CurriculumSystem cs : values()) {
            if (cs.name().equals(code)) return cs;
        }
        return SYSTEM_844;
    }

    public List<PresetGrade> getPresetGrades() {
        return switch (this) {
            case SYSTEM_844 -> List.of(
                new PresetGrade(80, 100, "A", 12, "Excellent"),
                new PresetGrade(75, 79, "A-", 11, "Very Good"),
                new PresetGrade(70, 74, "B+", 10, "Good"),
                new PresetGrade(65, 69, "B", 9, "Good"),
                new PresetGrade(60, 64, "B-", 8, "Fairly Good"),
                new PresetGrade(55, 59, "C+", 7, "Average"),
                new PresetGrade(50, 54, "C", 6, "Average"),
                new PresetGrade(45, 49, "C-", 5, "Below Average"),
                new PresetGrade(40, 44, "D+", 4, "Weak"),
                new PresetGrade(35, 39, "D", 3, "Weak"),
                new PresetGrade(30, 34, "D-", 2, "Poor"),
                new PresetGrade(0, 29, "E", 1, "Fail")
            );
            case CBC -> List.of(
                new PresetGrade(80, 100, "EE", 4, "Exceeding Expectation"),
                new PresetGrade(60, 79, "ME", 3, "Meeting Expectation"),
                new PresetGrade(40, 59, "AE", 2, "Approaching Expectation"),
                new PresetGrade(0, 39, "BE", 1, "Below Expectation")
            );
        };
    }

    public record PresetGrade(int min, int max, String grade, int points, String remarks) {}
}
