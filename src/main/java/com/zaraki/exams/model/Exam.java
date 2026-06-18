package com.zaraki.exams.model;

public class Exam {
    private Long id;
    private String academicYear;
    private String term;
    private String examSeries;

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
}
