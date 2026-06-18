package com.zaraki.exams;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.Exam;
import com.zaraki.exams.model.Mark;
import com.zaraki.exams.model.Student;
import com.zaraki.exams.model.Subject;
import com.zaraki.exams.repository.MarksRepository;

import java.sql.*;
import java.util.*;

public class Main {

    private static final DatabaseEngine db = DatabaseEngine.getInstance();
    private static final MarksRepository marksRepo = new MarksRepository();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("   ZARAKI EXAM ANALYSIS SYSTEM v1.0.0");
        System.out.println("============================================");
        System.out.println(" Database initialised successfully.");
        System.out.println();

        while (true) {
            System.out.println("--- MAIN MENU ---");
            System.out.println(" 1  Add Student");
            System.out.println(" 2  Add Subject");
            System.out.println(" 3  Create Exam");
            System.out.println(" 4  Enter Marks (batch demo)");
            System.out.println(" 5  View Students");
            System.out.println(" 6  View Subjects");
            System.out.println(" 7  View Exams");
            System.out.println(" 8  View Marks by Exam");
            System.out.println(" 0  Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> addStudent();
                case "2" -> addSubject();
                case "3" -> createExam();
                case "4" -> batchMarksDemo();
                case "5" -> viewStudents();
                case "6" -> viewSubjects();
                case "7" -> viewExams();
                case "8" -> viewMarksByExam();
                case "0" -> { System.out.println("Goodbye."); return; }
                default -> System.out.println("Invalid option.");
            }
            System.out.println();
        }
    }

    // ---- Students ----

    private static void addStudent() {
        try {
            System.out.print("Admission number: ");
            String adm = scanner.nextLine();
            System.out.print("Full name: ");
            String name = scanner.nextLine();
            System.out.print("Form (1-4): ");
            int form = Integer.parseInt(scanner.nextLine());
            System.out.print("Stream: ");
            String stream = scanner.nextLine();

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)")) {
                ps.setString(1, adm);
                ps.setString(2, name);
                ps.setInt(3, form);
                ps.setString(4, stream);
                ps.executeUpdate();
                System.out.println("Student added.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void viewStudents() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, full_name, form, stream FROM students")) {
            System.out.printf("%-4s %-14s %-25s %-4s %-10s%n", "ID", "Admission", "Name", "Form", "Stream");
            System.out.println("-".repeat(60));
            while (rs.next()) {
                System.out.printf("%-4d %-14s %-25s %-4d %-10s%n",
                        rs.getInt("id"), rs.getString("admission_number"),
                        rs.getString("full_name"), rs.getInt("form"),
                        rs.getString("stream"));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---- Subjects ----

    private static void addSubject() {
        try {
            System.out.print("Subject code: ");
            String code = scanner.nextLine();
            System.out.print("Subject name: ");
            String name = scanner.nextLine();
            System.out.print("Department: ");
            String dept = scanner.nextLine();
            System.out.print("Grouping (Compulsory/Elective): ");
            String grp = scanner.nextLine();

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)")) {
                ps.setString(1, code);
                ps.setString(2, name);
                ps.setString(3, dept);
                ps.setString(4, grp);
                ps.executeUpdate();
                System.out.println("Subject added.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void viewSubjects() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, subject_code, subject_name, department, grouping FROM subjects")) {
            System.out.printf("%-4s %-8s %-25s %-15s %-12s%n", "ID", "Code", "Name", "Dept", "Grouping");
            System.out.println("-".repeat(70));
            while (rs.next()) {
                System.out.printf("%-4d %-8s %-25s %-15s %-12s%n",
                        rs.getInt("id"), rs.getString("subject_code"),
                        rs.getString("subject_name"), rs.getString("department"),
                        rs.getString("grouping"));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---- Exams ----

    private static void createExam() {
        try {
            System.out.print("Academic year (e.g. 2026): ");
            String year = scanner.nextLine();
            System.out.print("Term (Term 1/2/3): ");
            String term = scanner.nextLine();
            System.out.print("Exam series (e.g. End-Term): ");
            String series = scanner.nextLine();

            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO exams (academic_year, term, exam_series) VALUES (?,?,?)")) {
                ps.setString(1, year);
                ps.setString(2, term);
                ps.setString(3, series);
                ps.executeUpdate();
                System.out.println("Exam created.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void viewExams() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, academic_year, term, exam_series FROM exams")) {
            System.out.printf("%-4s %-12s %-10s %-15s%n", "ID", "Year", "Term", "Series");
            System.out.println("-".repeat(45));
            while (rs.next()) {
                System.out.printf("%-4d %-12s %-10s %-15s%n",
                        rs.getInt("id"), rs.getString("academic_year"),
                        rs.getString("term"), rs.getString("exam_series"));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---- Marks (batch demo) ----

    private static void batchMarksDemo() {
        try {
            System.out.print("Exam ID: ");
            long examId = Long.parseLong(scanner.nextLine());

            String sql = "SELECT id FROM students";
            List<Long> studentIds = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) studentIds.add(rs.getLong("id"));
            }

            sql = "SELECT id FROM subjects";
            List<Long> subjectIds = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) subjectIds.add(rs.getLong("id"));
            }

            if (studentIds.isEmpty() || subjectIds.isEmpty()) {
                System.out.println("Add some students and subjects first.");
                return;
            }

            Random rand = new Random();
            List<Mark> marks = new ArrayList<>();
            for (long sid : studentIds) {
                for (long subjId : subjectIds) {
                    double score = 10 + rand.nextDouble() * 90;
                    marks.add(new Mark(examId, sid, subjId, Math.round(score * 100.0) / 100.0));
                }
            }

            marksRepo.batchInsert(marks);
            System.out.println("Inserted " + marks.size() + " marks in a single transaction.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void viewMarksByExam() {
        try {
            System.out.print("Exam ID: ");
            long examId = Long.parseLong(scanner.nextLine());
            List<Mark> marks = marksRepo.findByExamId(examId);
            if (marks.isEmpty()) {
                System.out.println("No marks for this exam.");
                return;
            }
            System.out.printf("%-10s %-10s %-10s %-8s%n", "StudentID", "SubjectID", "Score", "Grade");
            System.out.println("-".repeat(42));
            for (Mark m : marks) {
                System.out.printf("%-10d %-10d %-8.2f %-8s%n",
                        m.getStudentId(), m.getSubjectId(),
                        m.getScore(), m.getGradeAchieved());
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
