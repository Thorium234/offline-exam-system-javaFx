package com.zaraki.exams.model;

public class Subject {
    private Long id;
    private String subjectCode;
    private String subjectName;
    private String department;
    private String grouping;

    public Subject() {}

    public Subject(String subjectCode, String subjectName, String department, String grouping) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.department = department;
        this.grouping = grouping;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getGrouping() { return grouping; }
    public void setGrouping(String grouping) { this.grouping = grouping; }
}
