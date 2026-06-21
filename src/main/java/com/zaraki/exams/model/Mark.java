package com.zaraki.exams.model;

import java.util.Objects;

public class Mark {
    private long examId;
    private long studentId;
    private long subjectId;
    private double score;
    private String gradeAchieved;
    private int pointsAchieved;
    private String status;
    private String teacherComment;
    private String teacherName;
    private double deviation;

    public Mark() {}

    public Mark(long examId, long studentId, long subjectId, double score) {
        this.examId = examId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.score = score;
    }

    public long getExamId() { return examId; }
    public void setExamId(long examId) { this.examId = examId; }

    public long getStudentId() { return studentId; }
    public void setStudentId(long studentId) { this.studentId = studentId; }

    public long getSubjectId() { return subjectId; }
    public void setSubjectId(long subjectId) { this.subjectId = subjectId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getGradeAchieved() { return gradeAchieved; }
    public void setGradeAchieved(String gradeAchieved) { this.gradeAchieved = gradeAchieved; }

    public int getPointsAchieved() { return pointsAchieved; }
    public void setPointsAchieved(int pointsAchieved) { this.pointsAchieved = pointsAchieved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public double getDeviation() { return deviation; }
    public void setDeviation(double deviation) { this.deviation = deviation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mark mark)) return false;
        return examId == mark.examId && studentId == mark.studentId && subjectId == mark.subjectId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(examId, studentId, subjectId);
    }
}
