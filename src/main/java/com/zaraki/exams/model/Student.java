package com.zaraki.exams.model;

public class Student {
    private Long id;
    private String admissionNumber;
    private String fullName;
    private int form;
    private String stream;
    private String status;

    public Student() {}

    public Student(String admissionNumber, String fullName, int form, String stream, String status) {
        this.admissionNumber = admissionNumber;
        this.fullName = fullName;
        this.form = form;
        this.stream = stream;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAdmissionNumber() { return admissionNumber; }
    public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public int getForm() { return form; }
    public void setForm(int form) { this.form = form; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
