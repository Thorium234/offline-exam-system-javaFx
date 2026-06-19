package com.zaraki.exams.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Exam {
    private Long id;
    private String academicYear;
    private String term;
    private String examSeries;
    private boolean released;
    private String releasedBy;
    private LocalDateTime releasedAt;

    public Exam() {}

    public Exam(String academicYear, String term, String examSeries) {
        this.academicYear = academicYear;
        this.term = term;
        this.examSeries = examSeries;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getExamSeries() { return examSeries; }
    public void setExamSeries(String examSeries) { this.examSeries = examSeries; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public String getReleasedBy() { return releasedBy; }
    public void setReleasedBy(String releasedBy) { this.releasedBy = releasedBy; }

    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Exam exam)) return false;
        return released == exam.released && Objects.equals(id, exam.id) && Objects.equals(academicYear, exam.academicYear) && Objects.equals(term, exam.term) && Objects.equals(examSeries, exam.examSeries) && Objects.equals(releasedBy, exam.releasedBy) && Objects.equals(releasedAt, exam.releasedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, academicYear, term, examSeries, released, releasedBy, releasedAt);
    }
}
