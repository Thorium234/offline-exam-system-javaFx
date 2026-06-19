package com.zaraki.exams.model;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student student)) return false;
        return form == student.form && Objects.equals(id, student.id) && Objects.equals(admissionNumber, student.admissionNumber) && Objects.equals(fullName, student.fullName) && Objects.equals(stream, student.stream) && Objects.equals(status, student.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, admissionNumber, fullName, form, stream, status);
    }
}
