package com.zaraki.exams;

import com.zaraki.exams.auth.PasswordUtils;
import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.util.Random;
import java.util.logging.Logger;

public class SeedData {

    private static final Logger LOG = LoggerUtil.getLogger();

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
        LOG.info("Seeding all data...");
        int total = 0;
        seedSubjects();
        seedDefaultGrades();
        for (int form = 1; form <= 4; form++) {
            total += seedStudents(form, "A");
            total += seedStudents(form, "B");
        }
        LOG.info("Seeded " + total + " students");
        return total;
    }

    public void seedDefaultGrades() throws SQLException {
        if (hasDefaultGrades()) {
            LOG.fine("Default grades already exist, skipping");
            return;
        }
        LOG.info("Seeding default grading scales");
        String sql = "INSERT INTO grading_scales (subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (NULL,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Object[][] grades = {
                {80.0, 100.0, "A",  12, "Excellent"},
                {75.0, 79.0,  "A-", 11, "Very Good"},
                {70.0, 74.0,  "B+", 10, "Good"},
                {65.0, 69.0,  "B",  9,  "Good"},
                {60.0, 64.0,  "B-", 8,  "Fairly Good"},
                {55.0, 59.0,  "C+", 7,  "Average"},
                {50.0, 54.0,  "C",  6,  "Average"},
                {45.0, 49.0,  "C-", 5,  "Below Average"},
                {40.0, 44.0,  "D+", 4,  "Below Average"},
                {35.0, 39.0,  "D",  3,  "Weak"},
                {30.0, 34.0,  "D-", 2,  "Weak"},
                {0.0,  29.0,  "E",  1,  "Poor"}
            };
            for (Object[] g : grades) {
                ps.setDouble(1, (double) g[0]);
                ps.setDouble(2, (double) g[1]);
                ps.setString(3, (String) g[2]);
                ps.setInt(4, (int) g[3]);
                ps.setString(5, (String) g[4]);
                ps.executeUpdate();
            }
        }
    }

    private boolean hasDefaultGrades() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM grading_scales WHERE subject_id IS NULL")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public void seedSubjects() throws SQLException {
        LOG.info("Seeding subjects");
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

    public void seedTeachers() throws SQLException {
        if (hasTeachers()) {
            LOG.fine("Teachers already exist, skipping");
            return;
        }
        LOG.info("Seeding teachers");
        String sql = "INSERT OR IGNORE INTO users (username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)";
        String[][] teachers = {
            {"teacher1", "Alice Kamau"}, {"teacher2", "Bob Otieno"}, {"teacher3", "Carol Wanjiku"},
            {"teacher4", "David Mwangi"}, {"teacher5", "Eve Njoroge"}, {"teacher6", "Frank Ochieng"},
            {"teacher7", "Grace Akinyi"}, {"teacher8", "Henry Barasa"}, {"teacher9", "Ivy Nyambura"},
            {"teacher10", "Jack Kiprop"}
        };
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] t : teachers) {
                String salt = PasswordUtils.generateSalt();
                String hash = PasswordUtils.hashPassword("teacher", salt);
                ps.setString(1, t[0]);
                ps.setString(2, hash);
                ps.setString(3, salt);
                ps.setString(4, t[1]);
                ps.setString(5, "teacher");
                ps.executeUpdate();
            }
        }
    }

    private boolean hasTeachers() throws SQLException {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role='teacher'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public void hardReset() throws SQLException {
        LOG.warning("Performing hard reset of all data");
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                st.executeUpdate("DELETE FROM marks");
                st.executeUpdate("DELETE FROM exam_subjects");
                st.executeUpdate("DELETE FROM student_subjects");
                st.executeUpdate("DELETE FROM teacher_subjects");
                st.executeUpdate("DELETE FROM stream_subjects");
                st.executeUpdate("DELETE FROM students");
                st.executeUpdate("DELETE FROM exams");
                st.executeUpdate("DELETE FROM grading_scales");
                st.executeUpdate("DELETE FROM users WHERE role != 'admin'");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public int seedStudents(int form, String stream) throws SQLException {
        LOG.info("Seeding students for Form " + form + " " + stream);
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
        LOG.info("Seeding marks for exam " + examId);
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
                        double score = Math.round((20 + rng.nextDouble() * 61) * 10.0) / 10.0;
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
