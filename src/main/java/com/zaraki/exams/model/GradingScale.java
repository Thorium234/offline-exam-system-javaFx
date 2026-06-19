package com.zaraki.exams.model;

import java.util.Objects;

public class GradingScale {
    private Long id;
    private Long subjectId;
    private double minimumMark;
    private double maximumMark;
    private String grade;
    private int points;
    private String remarks;

    public GradingScale() {}

    public GradingScale(Long subjectId, double minimumMark, double maximumMark,
                        String grade, int points, String remarks) {
        this.subjectId = subjectId;
        this.minimumMark = minimumMark;
        this.maximumMark = maximumMark;
        this.grade = grade;
        this.points = points;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public double getMinimumMark() { return minimumMark; }
    public void setMinimumMark(double minimumMark) { this.minimumMark = minimumMark; }

    public double getMaximumMark() { return maximumMark; }
    public void setMaximumMark(double maximumMark) { this.maximumMark = maximumMark; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GradingScale that)) return false;
        return Double.compare(minimumMark, that.minimumMark) == 0 && Double.compare(maximumMark, that.maximumMark) == 0 && points == that.points && Objects.equals(id, that.id) && Objects.equals(subjectId, that.subjectId) && Objects.equals(grade, that.grade) && Objects.equals(remarks, that.remarks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectId, minimumMark, maximumMark, grade, points, remarks);
    }
}
