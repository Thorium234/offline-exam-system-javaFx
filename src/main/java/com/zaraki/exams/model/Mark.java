package com.zaraki.exams.model;

public class Mark {
    private long examId;
    private long studentId;
    private long subjectId;
    private double score;
    private String gradeAchieved;
    private int pointsAchieved;

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
}
