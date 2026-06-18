package com.zaraki.exams.reporting;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zaraki.exams.database.DatabaseEngine;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReportCardGenerator {

    private final DatabaseEngine db;

    public ReportCardGenerator() {
        this.db = DatabaseEngine.getInstance();
    }

    public void generateStudentReport(long examId, long studentId, Path outputPath) {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            doc.open();

            addHeader(doc, examId, studentId);
            addStudentInfo(doc, examId, studentId);
            addSubjectTable(doc, examId, studentId);
            addSummary(doc, examId, studentId);
            addPerformanceIndicator(doc, examId, studentId);

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate report card", e);
        }
    }

    private void addHeader(Document doc, long examId, long studentId) throws DocumentException {
        String examSql = "SELECT academic_year, term, exam_series FROM exams WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(examSql)) {
            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();
            String examInfo = "";
            if (rs.next()) {
                examInfo = rs.getString("academic_year") + " - " + rs.getString("term") + " - " + rs.getString("exam_series");
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("THORIUM EXAM ANALYSIS SYSTEM", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Paragraph school = new Paragraph("Kenya Secondary School - Official Report Form", subFont);
            school.setAlignment(Element.ALIGN_CENTER);
            doc.add(school);

            Paragraph exam = new Paragraph("Exam: " + examInfo, subFont);
            exam.setAlignment(Element.ALIGN_CENTER);
            doc.add(exam);

            Paragraph date = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subFont);
            date.setAlignment(Element.ALIGN_CENTER);
            doc.add(date);

            doc.add(Chunk.NEWLINE);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addStudentInfo(Document doc, long examId, long studentId) throws DocumentException {
        String sql = "SELECT s.admission_number, s.full_name, s.form, s.stream FROM students s WHERE s.id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new RuntimeException("Student not found");

            Font f = FontFactory.getFont(FontFactory.HELVETICA, 11);
            PdfPTable infoTable = new PdfPTable(4);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingBefore(6);
            infoTable.setSpacingAfter(6);

            infoTable.addCell(new Phrase("Admission: " + rs.getString("admission_number"), f));
            infoTable.addCell(new Phrase("Name: " + rs.getString("full_name"), f));
            infoTable.addCell(new Phrase("Form: " + rs.getString("form"), f));
            infoTable.addCell(new Phrase("Stream: " + rs.getString("stream"), f));

            doc.add(infoTable);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSubjectTable(Document doc, long examId, long studentId) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(8);
        float[] widths = {4f, 2f, 1.5f, 1.5f, 1.5f, 3f};
        try { table.setWidths(widths); } catch (Exception ignored) {}

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        String[] headers = {"Subject", "Score", "Grade", "Points", "Pos", "Remarks"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(26, 35, 126));
            cell.setPadding(5);
            table.addCell(cell);
        }

        String sql = """
            SELECT sub.subject_name, m.score, m.grade_achieved, m.points_achieved,
                   m.score,
                   (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                    WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id
                      AND m2.score > m.score) AS position,
                   (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2
                    WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id) AS total_students,
                   COALESCE(gs.remarks, '') AS remarks
            FROM marks m
            JOIN subjects sub ON sub.id = m.subject_id
            LEFT JOIN grading_scales gs ON (gs.subject_id IS NULL OR gs.subject_id = m.subject_id)
                AND m.score BETWEEN gs.minimum_mark AND gs.maximum_mark
            WHERE m.exam_id = ? AND m.student_id = ?
            GROUP BY m.subject_id
            ORDER BY sub.subject_name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();

            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                table.addCell(new Phrase(rs.getString("subject_name"), rowFont));
                table.addCell(new Phrase(rs.getObject("score") != null ? String.valueOf(rs.getDouble("score")) : "-", rowFont));
                table.addCell(new Phrase(rs.getString("grade_achieved") != null ? rs.getString("grade_achieved") : "-", rowFont));
                table.addCell(new Phrase(rs.getObject("points_achieved") != null ? String.valueOf(rs.getInt("points_achieved")) : "-", rowFont));
                int pos = rs.getInt("position");
                int total = rs.getInt("total_students");
                table.addCell(new Phrase(rs.wasNull() ? "-" : pos + "/" + total, rowFont));
                table.addCell(new Phrase(rs.getString("remarks") != null ? rs.getString("remarks") : "", rowFont));
            }

            if (!hasData) {
                for (int i = 0; i < 6; i++) {
                    table.addCell(new Phrase("No marks recorded", rowFont));
                }
            }
            doc.add(table);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSummary(Document doc, long examId, long studentId) throws DocumentException {
        String sql = """
            SELECT
                ROUND(SUM(m.score), 1) AS total_marks,
                COALESCE(SUM(m.points_achieved), 0) AS total_points,
                ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points,
                (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2 WHERE m2.exam_id = ?) AS total_students,
                (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                 JOIN (SELECT student_id, SUM(COALESCE(points_achieved, 0)) AS total_pts FROM marks WHERE exam_id = ? GROUP BY student_id) s2
                 ON m2.student_id = s2.student_id
                 WHERE m2.exam_id = ? AND s2.total_pts >
                    (SELECT COALESCE(SUM(COALESCE(points_achieved, 0)), 0) FROM marks WHERE exam_id = ? AND student_id = ?)
                ) AS class_rank,
                (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2
                 JOIN students s2 ON s2.id = m2.student_id
                 WHERE m2.exam_id = ? AND s2.stream = (SELECT stream FROM students WHERE id = ?)) AS stream_size,
                (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                 JOIN students s2 ON s2.id = m2.student_id
                 JOIN (SELECT student_id, SUM(COALESCE(points_achieved, 0)) AS total_pts FROM marks WHERE exam_id = ? GROUP BY student_id) s3
                 ON m2.student_id = s3.student_id
                 WHERE m2.exam_id = ? AND s2.stream = (SELECT stream FROM students WHERE id = ?)
                   AND s3.total_pts >
                    (SELECT COALESCE(SUM(COALESCE(points_achieved, 0)), 0) FROM marks WHERE exam_id = ? AND student_id = ?)
                ) AS stream_rank,
                ROUND(COALESCE(AVG(m.score), 0), 1) AS avg_score
            FROM marks m
            WHERE m.exam_id = ? AND m.student_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, examId);
            ps.setLong(3, examId);
            ps.setLong(4, examId);
            ps.setLong(5, studentId);
            ps.setLong(6, examId);
            ps.setLong(7, studentId);
            ps.setLong(8, examId);
            ps.setLong(9, examId);
            ps.setLong(10, studentId);
            ps.setLong(11, examId);
            ps.setLong(12, studentId);
            ps.setLong(13, examId);
            ps.setLong(14, studentId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
                PdfPTable summary = new PdfPTable(4);
                summary.setWidthPercentage(100);
                summary.setSpacingBefore(8);

                summary.addCell(new Phrase("Total Marks: " + rs.getDouble("total_marks"), f));
                summary.addCell(new Phrase("Total Points: " + rs.getInt("total_points"), f));
                summary.addCell(new Phrase("Mean Points: " + rs.getDouble("mean_points"), f));
                summary.addCell(new Phrase("Avg Score: " + rs.getDouble("avg_score"), f));

                PdfPCell classPos = new PdfPCell(new Phrase("Class Position: " + rs.getInt("class_rank") + "/" + rs.getInt("total_students"), f));
                PdfPCell streamPos = new PdfPCell(new Phrase("Stream Position: " + rs.getInt("stream_rank") + "/" + rs.getInt("stream_size"), f));
                classPos.setColspan(2);
                streamPos.setColspan(2);
                summary.addCell(classPos);
                summary.addCell(streamPos);

                doc.add(summary);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addPerformanceIndicator(Document doc, long examId, long studentId) throws DocumentException {
        String sql = """
            SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m WHERE m.exam_id = ? AND m.student_id = ?
            """;
        String prevSql = """
            SELECT COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m
            JOIN exams e ON e.id = m.exam_id
            WHERE m.student_id = ?
              AND e.academic_year = (SELECT academic_year FROM exams WHERE id = ?)
              AND e.id < ?
            ORDER BY e.id DESC LIMIT 1
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement prevPs = conn.prepareStatement(prevSql)) {

            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            int currentPoints = rs.next() ? rs.getInt("total_points") : 0;

            prevPs.setLong(1, studentId);
            prevPs.setLong(2, examId);
            prevPs.setLong(3, examId);
            ResultSet prevRs = prevPs.executeQuery();
            int prevPoints = prevRs.next() ? prevRs.getInt("total_points") : -1;

            Font f = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Paragraph indicator = new Paragraph();
            indicator.setSpacingBefore(12);
            if (prevPoints >= 0) {
                int diff = currentPoints - prevPoints;
                if (diff > 0) {
                    indicator.add(new Phrase("Performance Trend: Improved by " + diff + " points from previous exam.", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.GREEN)));
                } else if (diff < 0) {
                    indicator.add(new Phrase("Performance Trend: Dropped by " + Math.abs(diff) + " points from previous exam.", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.RED)));
                } else {
                    indicator.add(new Phrase("Performance Trend: Maintained same points as previous exam.", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.ORANGE)));
                }
            } else {
                indicator.add(new Phrase("Performance Trend: First exam - no previous data for comparison.", FontFactory.getFont(FontFactory.HELVETICA, 11, Color.GRAY)));
            }
            doc.add(indicator);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
