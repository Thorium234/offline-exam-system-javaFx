package com.zaraki.exams;

import com.zaraki.exams.database.DatabaseEngine;

import java.sql.*;
import java.util.Random;

public class SeedData {

    private final DatabaseEngine db;

    private static final String[] FIRST_NAMES = {
        "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
        "David", "Elizabeth", "William", "Susan", "Richard", "Jessica", "Joseph", "Sarah",
        "Thomas", "Karen", "Charles", "Lisa", "Christopher", "Nancy", "Daniel", "Betty",
        "Matthew", "Margaret", "Anthony", "Sandra", "Mark", "Ashley", "Donald", "Dorothy",
        "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle",
        "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah",
        "Timothy", "Stephanie", "Ronald", "Rebecca", "Edward", "Sharon", "Jason", "Laura",
        "Jeffrey", "Cynthia", "Ryan", "Kathleen", "Jacob", "Amy", "Gary", "Angela"
    };

    private static final String[] LAST_NAMES = {
        "Kamau", "Wanjiku", "Mwangi", "Njoroge", "Ochieng", "Akinyi", "Barasa", "Nyambura",
        "Kiprop", "Chebet", "Mutua", "Muthoni", "Kimanzi", "Wambui", "Odhiambo", "Atieno",
        "Ndegwa", "Nyaguthii", "Kosgei", "Jepkoech", "Waweru", "Njeri", "Omari", "Mwikali",
        "Omondi", "Adhiambo", "Kariuki", "Wairimu", "Rotich", "Jerotich", "Kipkemei", "Chepkoech",
        "Mbugua", "Nyambura", "Langat", "Chelangat", "Mukuria", "Wangechi", "Ngetich", "Kipchumba"
    };

    private static final String[][] SUBJECTS = {
        new String[]{"ENG", "English", "Languages", "Compulsory"},
        new String[]{"KIS", "Kiswahili", "Languages", "Compulsory"},
        new String[]{"MAT", "Mathematics", "Mathematics", "Compulsory"},
        new String[]{"BIO", "Biology", "Sciences", "Compulsory"},
        new String[]{"PHY", "Physics", "Sciences", "Compulsory"},
        new String[]{"CHE", "Chemistry", "Sciences", "Compulsory"},
        new String[]{"HIS", "History", "Humanities", "Compulsory"},
        new String[]{"GEO", "Geography", "Humanities", "Compulsory"},
        new String[]{"CRE", "C.R.E.", "Humanities", "Compulsory"},
        new String[]{"BUS", "Business Studies", "Humanities", "Elective"},
        new String[]{"AGR", "Agriculture", "Humanities", "Elective"},
        new String[]{"COM", "Computer Studies", "Technical", "Elective"}
    };

    public SeedData() {
        this.db = DatabaseEngine.getInstance();
    }

    public int seedAll() throws SQLException {
        int total = 0;
        seedSubjects();
        for (int form = 1; form <= 4; form++) {
            total += seedStudents(form, "A");
            total += seedStudents(form, "B");
        }
        return total;
    }

    public void seedSubjects() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO subjects (subject_code, subject_name, department, grouping) VALUES (?,?,?,?)")) {
            for (String[] s : SUBJECTS) {
                ps.setString(1, s[0]);
                ps.setString(2, s[1]);
                ps.setString(3, s[2]);
                ps.setString(4, s[3]);
                ps.executeUpdate();
            }
        }
    }

    public int seedStudents(int form, String stream) throws SQLException {
        String sql = "INSERT OR IGNORE INTO students (admission_number, full_name, form, stream) VALUES (?,?,?,?)";
        int count = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Random rng = new Random(form * 100 + stream.charAt(0));
            for (int i = 1; i <= 20; i++) {
                String adm = String.format("%04d/F%d%s", i, form, stream);
                String fName = FIRST_NAMES[rng.nextInt(FIRST_NAMES.length)];
                String lName = LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
                String fullName = fName + " " + lName;
                ps.setString(1, adm);
                ps.setString(2, fullName);
                ps.setInt(3, form);
                ps.setString(4, stream);
                try { ps.executeUpdate(); count++; } catch (SQLException ignored) {}
            }
        }
        return count;
    }

    public int seedMarks(long examId) throws SQLException {
        String subjSql = "SELECT id FROM subjects";
        String studentSql = "SELECT id FROM students";
        String insertSql = "INSERT OR REPLACE INTO marks (exam_id, student_id, subject_id, score) VALUES (?,?,?,?)";

        try (Connection conn = db.getConnection();
             Statement subjSt = conn.createStatement();
             Statement stuSt = conn.createStatement();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            ResultSet subjRs = subjSt.executeQuery(subjSql);
            java.util.List<Long> subjectIds = new java.util.ArrayList<>();
            while (subjRs.next()) subjectIds.add(subjRs.getLong("id"));

            ResultSet stuRs = stuSt.executeQuery(studentSql);
            java.util.List<Long> studentIds = new java.util.ArrayList<>();
            while (stuRs.next()) studentIds.add(stuRs.getLong("id"));

            Random rng = new Random(examId);
            conn.setAutoCommit(false);
            try {
                int count = 0;
                for (long sid : studentIds) {
                    for (long subjId : subjectIds) {
                        double score = Math.round((20 + rng.nextDouble() * 60) * 10.0) / 10.0;
                        ps.setLong(1, examId);
                        ps.setLong(2, sid);
                        ps.setLong(3, subjId);
                        ps.setDouble(4, score);
                        ps.addBatch();
                        count++;
                        if (count % 200 == 0) ps.executeBatch();
                    }
                }
                ps.executeBatch();
                conn.commit();
                return (int) ((long) studentIds.size() * subjectIds.size());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public long getFirstExamId() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM exams ORDER BY id LIMIT 1")) {
            return rs.next() ? rs.getLong("id") : -1;
        }
    }
}
