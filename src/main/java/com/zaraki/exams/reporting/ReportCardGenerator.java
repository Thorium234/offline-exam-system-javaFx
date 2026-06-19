package com.zaraki.exams.reporting;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.zaraki.exams.config.SettingsManager;
import com.zaraki.exams.database.DatabaseEngine;
import static com.zaraki.exams.database.DatabaseEngine.validateFilterColumn;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ReportCardGenerator {

    private final DatabaseEngine db;

    public ReportCardGenerator() {
        this.db = DatabaseEngine.getInstance();
    }

    static {
        SettingsManager sm = new SettingsManager();
        String logoPath = sm.getLogoPath();
        if (logoPath != null && !logoPath.isBlank()) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(logoPath);
                if (java.nio.file.Files.exists(p))
                    backgroundLogoBytes = java.nio.file.Files.readAllBytes(p);
            } catch (Exception ignored) {}
        }
    }

    private static byte[] backgroundLogoBytes;

    private static class LogoBackground extends PdfPageEventHelper {
        @Override
        public void onStartPage(PdfWriter writer, Document doc) {
            if (backgroundLogoBytes == null) return;
            try {
                com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(backgroundLogoBytes);
                PdfContentByte cb = writer.getDirectContentUnder();
                float pageW = doc.getPageSize().getWidth();
                float pageH = doc.getPageSize().getHeight();
                float scale = Math.min(pageW / logo.getWidth(), pageH / logo.getHeight()) * 0.4f;
                logo.scalePercent(scale * 100);
                float x = (pageW - logo.getScaledWidth()) / 2;
                float y = (pageH - logo.getScaledHeight()) / 2;
                cb.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.08f);
                cb.setGState(gs);
                cb.addImage(logo, logo.getScaledWidth(), 0, 0, logo.getScaledHeight(), x, y);
                cb.restoreState();
            } catch (Exception ignored) {}
        }
    }

    public void generateStudentReport(long examId, long studentId, Path outputPath) {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            writer.setPageEvent(new LogoBackground());
            doc.open();

            addHeader(doc, examId, studentId);
            addStudentInfo(doc, examId, studentId);
            addSubjectTable(doc, examId, studentId);
            addSummary(doc, examId, studentId);
            addPerformanceIndicator(doc, examId, studentId);
        addTrendChart(doc, writer, studentId);
        addStamp(doc, writer);
        addFooterSecurity(doc, writer, examId, studentId);

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

            SettingsManager sm = new SettingsManager();
            String schoolName = sm.getSchoolName();
            String openingDate = sm.getOpeningDate();
            String closingDate = sm.getClosingDate();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("THORIUM EXAM ANALYSIS SYSTEM", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            String schoolLine = schoolName + " - Official Report Form";
            Paragraph school = new Paragraph(schoolLine, subFont);
            school.setAlignment(Element.ALIGN_CENTER);
            doc.add(school);

            Paragraph exam = new Paragraph("Exam: " + examInfo, subFont);
            exam.setAlignment(Element.ALIGN_CENTER);
            doc.add(exam);

            if (!openingDate.isBlank() || !closingDate.isBlank()) {
                String termLine = "";
                if (!openingDate.isBlank()) termLine += "Opens: " + openingDate;
                if (!openingDate.isBlank() && !closingDate.isBlank()) termLine += "  |  ";
                if (!closingDate.isBlank()) termLine += "Closes: " + closingDate;
                Paragraph datesP = new Paragraph(termLine, subFont);
                datesP.setAlignment(Element.ALIGN_CENTER);
                doc.add(datesP);
            }

            Paragraph date = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subFont);
            date.setAlignment(Element.ALIGN_CENTER);
            doc.add(date);

            doc.add(Chunk.NEWLINE);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addStudentInfo(Document doc, long examId, long studentId) throws DocumentException {
        String sql = "SELECT s.admission_number, s.full_name, s.form, s.stream, s.photo FROM students s WHERE s.id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new RuntimeException("Student not found");

            Font f = FontFactory.getFont(FontFactory.HELVETICA, 11);

            PdfPTable outerTable = new PdfPTable(2);
            outerTable.setWidthPercentage(100);
            outerTable.setSpacingBefore(6);
            outerTable.setSpacingAfter(6);
            float[] outerWidths = {3f, 1f};
            try { outerTable.setWidths(outerWidths); } catch (Exception ignored) {}

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.addCell(new Phrase("Admission: " + rs.getString("admission_number"), f));
            infoTable.addCell(new Phrase("Name: " + rs.getString("full_name"), f));
            infoTable.addCell(new Phrase("Form: " + rs.getString("form"), f));
            infoTable.addCell(new Phrase("Stream: " + rs.getString("stream"), f));

            PdfPCell leftCell = new PdfPCell(infoTable);
            leftCell.setBorder(PdfPCell.NO_BORDER);
            outerTable.addCell(leftCell);

            byte[] photoBytes = rs.getBytes("photo");
            if (photoBytes != null && photoBytes.length > 0) {
                try {
                    com.lowagie.text.Image photo = com.lowagie.text.Image.getInstance(photoBytes);
                    photo.scaleToFit(80, 100);
                    PdfPCell photoCell = new PdfPCell(photo);
                    photoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    photoCell.setBorder(PdfPCell.NO_BORDER);
                    outerTable.addCell(photoCell);
                } catch (Exception e) {
                    outerTable.addCell(new PdfPCell(new Phrase("", f)));
                }
            } else {
                outerTable.addCell(new PdfPCell(new Phrase("", f)));
            }

            doc.add(outerTable);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSubjectTable(Document doc, long examId, long studentId) throws DocumentException {
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(8);
        float[] widths = {3f, 1.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 2.5f};
        try { table.setWidths(widths); } catch (Exception ignored) {}

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        String[] headers = {"Subject", "Score", "Grade", "Points", "Pos", "Dev", "Cmt", "Remarks"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(26, 35, 126));
            cell.setPadding(5);
            table.addCell(cell);
        }

        String sql = """
            SELECT sub.subject_name, m.score, m.grade_achieved, m.points_achieved,
                   (SELECT COUNT(DISTINCT m2.student_id) + 1 FROM marks m2
                    WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id
                      AND m2.score > m.score) AS position,
                   (SELECT COUNT(DISTINCT m2.student_id) FROM marks m2
                    WHERE m2.exam_id = m.exam_id AND m2.subject_id = m.subject_id) AS total_students,
                   COALESCE(gs.remarks, '') AS remarks,
                   COALESCE(m.deviation, 0.0) AS deviation,
                   COALESCE(m.teacher_comment, '') AS teacher_comment,
                   COALESCE(m.teacher_name, '') AS teacher_name
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
                double dev = rs.getDouble("deviation");
                table.addCell(new Phrase(dev != 0 ? String.format("%+.1f", dev) : "0", rowFont));
                String teacherName = rs.getString("teacher_name");
                String comment = rs.getString("teacher_comment");
                StringBuilder cmtBuilder = new StringBuilder();
                if (teacherName != null && !teacherName.isBlank()) cmtBuilder.append(teacherName);
                if (comment != null && !comment.isBlank()) {
                    if (!cmtBuilder.isEmpty()) cmtBuilder.append(": ");
                    cmtBuilder.append(comment);
                }
                table.addCell(new Phrase(cmtBuilder.toString(), rowFont));
                table.addCell(new Phrase(rs.getString("remarks") != null ? rs.getString("remarks") : "", rowFont));
            }

            if (!hasData) {
                for (int i = 0; i < 8; i++) {
                    table.addCell(new Phrase("No marks recorded", rowFont));
                }
            }
            doc.add(table);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String meanPointsToGrade(double meanPoints) {
        if (meanPoints >= 12) return "A";
        if (meanPoints >= 11) return "A-";
        if (meanPoints >= 10) return "B+";
        if (meanPoints >= 9) return "B";
        if (meanPoints >= 8) return "B-";
        if (meanPoints >= 7) return "C+";
        if (meanPoints >= 6) return "C";
        if (meanPoints >= 5) return "C-";
        if (meanPoints >= 4) return "D+";
        if (meanPoints >= 3) return "D";
        if (meanPoints >= 2) return "D-";
        return "E";
    }

    private void addSummary(Document doc, long examId, long studentId) throws DocumentException {
        String sql = """
            SELECT
                ROUND(SUM(m.score), 1) AS total_marks,
                COALESCE(SUM(m.points_achieved), 0) AS total_points,
                ROUND(COALESCE(AVG(m.points_achieved), 0), 1) AS mean_points,
                COUNT(m.subject_id) AS subject_count
            FROM marks m
            WHERE m.exam_id = ? AND m.student_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                doc.add(new Paragraph("SUMMARY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(26, 35, 126))));

                double totalMarks = rs.getDouble("total_marks");
                int totalPoints = rs.getInt("total_points");
                double meanPoints = rs.getDouble("mean_points");
                int subjectCount = rs.getInt("subject_count");
                String meanGrade = meanPointsToGrade(meanPoints);

                Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
                String line = "Total Marks: " + totalMarks
                    + "  |  Total Points: " + totalPoints
                    + "  |  Mean Points: " + meanPoints
                    + "  |  Mean Grade: " + meanGrade;
                doc.add(new Paragraph(line, f));

                // Auto-remark based on performance band
                double maxPossible = subjectCount * 12.0;
                if (maxPossible > 0) {
                    double pct = (totalPoints / maxPossible) * 100;
                    SettingsManager sm = new SettingsManager();
                    String remark;
                    if (pct >= 70) {
                        remark = sm.getSetting("remark_high", "Excellent performance. Keep it up!");
                    } else if (pct >= 50) {
                        remark = sm.getSetting("remark_average", "Good performance. Room for improvement.");
                    } else {
                        remark = sm.getSetting("remark_low", "Needs more effort and focus.");
                    }
                    Font remarkFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.ITALIC, new Color(100, 100, 100));
                    Paragraph rp = new Paragraph("Performance Band (" + String.format("%.0f", pct) + "%): " + remark, remarkFont);
                    rp.setSpacingBefore(4);
                    doc.add(rp);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addTrendChart(Document doc, PdfWriter writer, long studentId) throws DocumentException {
        String sql = """
            SELECT e.id, e.academic_year || ' ' || e.term || ' ' || e.exam_series AS label,
                   COALESCE(SUM(m.points_achieved), 0) AS total_points
            FROM marks m
            JOIN exams e ON e.id = m.exam_id
            WHERE m.student_id = ?
            GROUP BY e.id, e.academic_year, e.term, e.exam_series
            ORDER BY e.id
            """;
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                labels.add(rs.getString("label"));
                values.add(rs.getDouble("total_points"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (values.size() < 2) return;

        PdfPTable container = new PdfPTable(1);
        container.setWidthPercentage(100);
        container.setSpacingBefore(12);

        Paragraph chartTitle = new Paragraph("PERFORMANCE TREND", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY));
        chartTitle.setAlignment(Element.ALIGN_CENTER);
        PdfPCell titleCell = new PdfPCell(chartTitle);
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setPaddingBottom(4);
        container.addCell(titleCell);

        PdfPCell chartCell = new PdfPCell();
        chartCell.setFixedHeight(150);
        chartCell.setBorder(PdfPCell.NO_BORDER);
        chartCell.setPaddingLeft(15);
        chartCell.setPaddingRight(15);

        chartCell.setCellEvent((cell, rect, canvases) -> {
            PdfContentByte cb = canvases[PdfPTable.BASECANVAS];

            BaseFont bf;
            try { bf = BaseFont.createFont(); } catch (Exception e) { return; }

            float marginLeft = 40;
            float marginBottom = 20;
            float marginRight = 10;
            float marginTop = 10;
            float x0 = rect.getLeft() + marginLeft;
            float y0 = rect.getBottom() + marginBottom;
            float w = rect.getWidth() - marginLeft - marginRight;
            float h = rect.getHeight() - marginBottom - marginTop;

            double minVal = values.stream().mapToDouble(v -> v).min().orElse(0);
            double maxVal = values.stream().mapToDouble(v -> v).max().orElse(1);
            double range = maxVal - minVal;
            if (range == 0) range = 1;
            double pad = range * 0.15;
            double yMin = Math.max(0, minVal - pad);
            double yMax = maxVal + pad;
            double yRange = yMax - yMin;

            // --- Y-axis label ---
            cb.beginText();
            cb.setFontAndSize(bf, 8);
            cb.showTextAligned(Element.ALIGN_CENTER, "Total Points", x0 - 25, y0 + h / 2, 90);
            cb.endText();

            // --- Draw axes ---
            cb.setColorStroke(new Color(180, 180, 180));
            cb.setLineWidth(1);
            cb.moveTo(x0, y0);
            cb.lineTo(x0 + w, y0);
            cb.stroke();
            cb.moveTo(x0, y0);
            cb.lineTo(x0, y0 + h);
            cb.stroke();

            // --- Y-axis ticks & labels ---
            int ticks = 4;
            for (int i = 0; i <= ticks; i++) {
                double val = yMin + (yRange * i / ticks);
                float y = y0 + (float) ((val - yMin) / yRange * h);
                cb.setColorStroke(new Color(220, 220, 220));
                cb.setLineDash(2, 2);
                cb.moveTo(x0, y);
                cb.lineTo(x0 + w, y);
                cb.stroke();
                cb.resetRGBColorStroke();

                cb.setColorFill(new Color(120, 120, 120));
                String tickLabel = String.valueOf((int) Math.round(val));
                cb.beginText();
                cb.setFontAndSize(bf, 7);
                cb.showTextAligned(Element.ALIGN_RIGHT, tickLabel, x0 - 3, y - 3, 0);
                cb.endText();
            }

            // --- Plot data ---
            List<float[]> pts = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                float x = x0 + w * (i + 1) / (values.size() + 1);
                float y = y0 + (float) ((values.get(i) - yMin) / yRange * h);
                pts.add(new float[]{x, y});
            }

            // Connect lines
            cb.setColorStroke(new Color(26, 35, 126));
            cb.setLineWidth(2);
            for (int i = 1; i < pts.size(); i++) {
                cb.moveTo(pts.get(i - 1)[0], pts.get(i - 1)[1]);
                cb.lineTo(pts.get(i)[0], pts.get(i)[1]);
                cb.stroke();
            }

            // Draw points
            cb.setColorFill(new Color(26, 35, 126));
            for (float[] p : pts) {
                cb.circle(p[0], p[1], 3.5f);
                cb.fill();
            }

            // X-axis label
            cb.setColorFill(new Color(80, 80, 80));
            cb.beginText();
            cb.setFontAndSize(bf, 8);
            cb.showTextAligned(Element.ALIGN_CENTER, "Exam #", x0 + w / 2, y0 - 14, 0);
            cb.endText();

            // X-axis tick labels
            for (int i = 0; i < values.size(); i++) {
                float x = x0 + w * (i + 1) / (values.size() + 1);
                String shortLabel = "E" + (i + 1);
                cb.beginText();
                cb.setFontAndSize(bf, 7);
                cb.showTextAligned(Element.ALIGN_CENTER, shortLabel, x, y0 - 7, 0);
                cb.endText();
            }
        });

        container.addCell(chartCell);

        // Legend
        Font legFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
        StringBuilder legendSb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) legendSb.append(", ");
            legendSb.append("E").append(i + 1).append("=");
            String examPart = labels.get(i).length() > 28 ? labels.get(i).substring(0, 28) + "..." : labels.get(i);
            legendSb.append(examPart);
        }
        Paragraph legend = new Paragraph(legendSb.toString(), legFont);
        legend.setAlignment(Element.ALIGN_CENTER);
        legend.setSpacingBefore(2);
        PdfPCell legCell = new PdfPCell(legend);
        legCell.setBorder(PdfPCell.NO_BORDER);
        container.addCell(legCell);

        doc.add(container);
    }

    private void addStamp(Document doc, PdfWriter writer) {
        String stampPath = new SettingsManager().getStampPath();
        if (stampPath == null || stampPath.isBlank()) return;
        try {
            com.lowagie.text.Image stamp = com.lowagie.text.Image.getInstance(stampPath);
            stamp.scaleToFit(100, 60);
            stamp.setAbsolutePosition(
                PageSize.A4.getWidth() - stamp.getScaledWidth() - 36,
                36
            );
            writer.getDirectContent().addImage(stamp);
        } catch (Exception ignored) {}
    }

    public void generateBulkStudentReports(long examId, String groupBy, String groupValue, Path outputPath) {
        String filterCol = DatabaseEngine.validateFilterColumn(groupBy.equals("stream") ? "stream" : "form");
        List<Long> studentIds = new ArrayList<>();
        String studentSql = "SELECT id FROM students WHERE " + filterCol + " = ? ORDER BY admission_number";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(studentSql)) {
            ps.setString(1, groupValue);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) studentIds.add(rs.getLong("id"));
        } catch (SQLException e) { throw new RuntimeException(e); }

        if (studentIds.isEmpty()) throw new RuntimeException("No students found for " + groupBy + ": " + groupValue);

        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            writer.setPageEvent(new LogoBackground());
            doc.open();

            for (int i = 0; i < studentIds.size(); i++) {
                if (i > 0) doc.newPage();
                long studentId = studentIds.get(i);
                addHeader(doc, examId, studentId);
                addStudentInfo(doc, examId, studentId);
                addSubjectTable(doc, examId, studentId);
                addSummary(doc, examId, studentId);
                addPerformanceIndicator(doc, examId, studentId);
                addTrendChart(doc, writer, studentId);
                addStamp(doc, writer);
                addFooterSecurity(doc, writer, examId, studentId);
            }

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate bulk report cards", e);
        }
    }

    public void generateStudentListPdf(String filterCol, String filterValue, Path outputPath) {
        filterCol = validateFilterColumn(filterCol);
        Document doc = new Document(PageSize.A4, 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            doc.open();

            Font tf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLUE);
            Paragraph title = new Paragraph("THORIUM EXAM ANALYSIS SYSTEM", tf);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Font sf = FontFactory.getFont(FontFactory.HELVETICA, 11);
            String groupLabel = filterCol.equals("form") ? "Form " + filterValue : filterCol.equals("stream") ? "Stream: " + filterValue : "All Students";
            Paragraph info = new Paragraph("Student List - " + groupLabel, sf);
            info.setAlignment(Element.ALIGN_CENTER);
            doc.add(info);
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            String[] headers = {"#", "Admission", "Name", "Stream"};
            Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, hf));
                c.setBackgroundColor(new Color(26, 35, 126)); c.setPadding(5);
                table.addCell(c);
            }

            String where = filterCol.isEmpty() ? "" : " WHERE " + filterCol + " = ?";
            String sql = "SELECT admission_number, full_name, form, stream FROM students" + where + " ORDER BY form, stream, admission_number";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!filterCol.isEmpty()) ps.setString(1, filterValue);
                ResultSet rs = ps.executeQuery();
                Font rf = FontFactory.getFont(FontFactory.HELVETICA, 10);
                int num = 1;
                while (rs.next()) {
                    table.addCell(new Phrase(String.valueOf(num++), rf));
                    table.addCell(new Phrase(rs.getString("admission_number"), rf));
                    table.addCell(new Phrase(rs.getString("full_name"), rf));
                    table.addCell(new Phrase("F" + rs.getInt("form") + " " + rs.getString("stream"), rf));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }

            doc.add(table);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate student list", e);
        }
    }

    public void generateGroupReport(long examId, String groupBy, String groupValue, Path outputPath) {
        Document doc = new Document(PageSize.A4.rotate(), 20, 20, 25, 25);
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLUE);
            Paragraph title = new Paragraph("THORIUM EXAM ANALYSIS SYSTEM - MERIT LIST", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String examInfo;
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT academic_year, term, exam_series FROM exams WHERE id = ?")) {
                ps.setLong(1, examId);
                ResultSet rs = ps.executeQuery();
                examInfo = rs.next() ? rs.getString("academic_year") + " - " + rs.getString("term") + " - " + rs.getString("exam_series") : "";
            }
            String groupLabel = groupBy.equals("stream") ? "Stream: " + groupValue : "Form: " + groupValue;
            Font sf = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Paragraph pInfo = new Paragraph("Exam: " + examInfo + "   |   " + groupLabel, sf);
            pInfo.setAlignment(Element.ALIGN_CENTER);
            doc.add(pInfo);
            doc.add(Chunk.NEWLINE);

            String filterCol = validateFilterColumn(groupBy.equals("stream") ? "stream" : "form");
            com.zaraki.exams.service.ExamAnalysisService analysis = new com.zaraki.exams.service.ExamAnalysisService();
            var reportData = analysis.computeMeritReport(examId, filterCol, groupValue);

            var subjects = reportData.subjects();
            var students = reportData.students();

            List<Long> subjIds = subjects.stream().map(com.zaraki.exams.service.ExamAnalysisService.MeritSubject::id).toList();
            List<String> subjCodes = subjects.stream().map(s -> s.code() != null && !s.code().isBlank() ? s.code() : "S" + (subjects.indexOf(s) + 1)).toList();

            int colCount = 2 + subjIds.size() * 2 + 5;
            PdfPTable table = new PdfPTable(colCount);
            table.setWidthPercentage(100);
            float[] widths = new float[colCount];
            widths[0] = 3f; widths[1] = 5f;
            int idx = 2;
            for (int i = 0; i < subjIds.size(); i++) {
                widths[idx++] = 1.8f; widths[idx++] = 1.2f;
            }
            widths[idx++] = 1.5f; widths[idx++] = 2f; widths[idx++] = 1.5f; widths[idx++] = 1.5f; widths[idx] = 1.2f;
            try { table.setWidths(widths); } catch (Exception ignored) {}

            Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE);
            Color headerBg = new Color(26, 35, 126);

            PdfPCell hCell = new PdfPCell(new Phrase("Adm", hf));
            hCell.setBackgroundColor(headerBg); hCell.setPadding(3); table.addCell(hCell);
            hCell = new PdfPCell(new Phrase("Name", hf));
            hCell.setBackgroundColor(headerBg); hCell.setPadding(3); table.addCell(hCell);

            for (String code : subjCodes) {
                String label = code.length() > 5 ? code.substring(0, 5) : code;
                PdfPCell sc = new PdfPCell(new Phrase(label, hf));
                sc.setBackgroundColor(headerBg); sc.setPadding(3); table.addCell(sc);
                PdfPCell pc = new PdfPCell(new Phrase("Pos", hf));
                pc.setBackgroundColor(headerBg); pc.setPadding(3); table.addCell(pc);
            }

            for (String agg : new String[]{"Dev", "T.Mks", "Pos", "Mean", "Gr"}) {
                PdfPCell ac = new PdfPCell(new Phrase(agg, hf));
                ac.setBackgroundColor(headerBg); ac.setPadding(3); table.addCell(ac);
            }

            Font rf = FontFactory.getFont(FontFactory.HELVETICA, 7);
            for (var student : students) {
                table.addCell(new Phrase(student.admissionNumber(), rf));
                table.addCell(new Phrase(student.fullName(), rf));

                for (long subjId : subjIds) {
                    var studentScores = student.scores();
                    boolean tookSubject = studentScores.containsKey(subjId);
                    if (tookSubject) {
                        double score = studentScores.get(subjId);
                        int pos = student.subjectPositions().getOrDefault(subjId, 0);
                        table.addCell(new Phrase(String.valueOf(score), rf));
                        table.addCell(new Phrase(String.valueOf(pos), rf));
                    } else {
                        table.addCell(new Phrase("-", rf));
                        table.addCell(new Phrase("-", rf));
                    }
                }

                var devs = student.deviations();
                double overallDev = devs.isEmpty() ? 0 : Math.round(devs.values().stream().mapToDouble(v -> v).average().orElse(0) * 10.0) / 10.0;

                int subjCount = (int) student.scores().values().stream().filter(v -> v > 0).count();
                if (subjCount == 0) subjCount = student.scores().size();
                double meanPts = subjCount > 0 ? Math.round((double) student.totalPoints() / subjCount * 10.0) / 10.0 : 0;
                String grade = meanPts >= 12 ? "A" : meanPts >= 11 ? "A-" : meanPts >= 10 ? "B+" : meanPts >= 9 ? "B" : meanPts >= 8 ? "B-" : meanPts >= 7 ? "C+" : meanPts >= 6 ? "C" : meanPts >= 5 ? "C-" : meanPts >= 4 ? "D+" : meanPts >= 3 ? "D" : meanPts >= 2 ? "D-" : "E";

                table.addCell(new Phrase(overallDev != 0 ? String.valueOf(overallDev) : "0", rf));
                table.addCell(new Phrase(String.valueOf(student.totalMarks()), rf));
                table.addCell(new Phrase(String.valueOf(student.rank()), rf));
                table.addCell(new Phrase(String.valueOf(meanPts), rf));
                table.addCell(new Phrase(grade, rf));
            }

            doc.add(table);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate merit list", e);
        }
    }

    private String computeSecurityHash(long examId, long studentId) {
        try {
            String raw = studentId + "|" + examId + "|" + LocalDateTime.now().toString();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return "ERROR";
        }
    }

    private void addFooterSecurity(Document doc, PdfWriter writer, long examId, long studentId) {
        try {
            String hashHex = computeSecurityHash(examId, studentId);
            String qrContent = "THORIUM-REPORT:" + hashHex + "|S" + studentId + "|E" + examId;

            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrWriter.encode(qrContent, BarcodeFormat.QR_CODE, 120, 120);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            com.lowagie.text.Image qrImage = com.lowagie.text.Image.getInstance(baos.toByteArray());
            qrImage.setAbsolutePosition(
                PageSize.A4.getWidth() - qrImage.getScaledWidth() - 36,
                36
            );
            writer.getDirectContent().addImage(qrImage);

            Font stampFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
            ColumnText.showTextAligned(writer.getDirectContent(),
                Element.ALIGN_LEFT,
                new Phrase("Sec: " + hashHex, stampFont),
                36, 32, 0);
        } catch (WriterException | IOException | DocumentException e) {
            // ignore QR errors silently
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

            Paragraph indicator = new Paragraph();
            indicator.setSpacingBefore(6);
            if (prevPoints >= 0) {
                int diff = currentPoints - prevPoints;
                String msg;
                Color c;
                if (diff > 0) { msg = "Trend: Improved by " + diff + " pts from previous exam"; c = Color.GREEN; }
                else if (diff < 0) { msg = "Trend: Dropped by " + Math.abs(diff) + " pts from previous exam"; c = Color.RED; }
                else { msg = "Trend: Maintained same points as previous exam"; c = Color.ORANGE; }
                indicator.add(new Phrase(msg, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, c)));
            } else {
                indicator.add(new Phrase("Trend: First exam - no previous data.", FontFactory.getFont(FontFactory.HELVETICA, 11, Color.GRAY)));
            }
            doc.add(indicator);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
