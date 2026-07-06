# Thorium Exam Analysis System v2

A lightweight, offline-first desktop GUI application for analyzing exam results and generating professional A4 report cards in Kenyan secondary schools.

Built with **Java 17+, JavaFX, SQLite, OpenPDF, ZXing, and Maven**.

## Features

- **Student Management** — Register and view students across forms (1–4) and streams; upload student photos (BLOB storage)
- **Student Subject Enrollment** — Assign individual students to subjects from their enrolled form/stream offerings
- **Stream-Based Subject Assignment** — Assign subjects to entire form/stream groups
- **Subject Configuration** — Define subjects with departments and groupings (Compulsory/Elective)
- **Exam Setup** — Academic year, term, and exam series management with subject out-of bounds
- **Marks Entry** — Per-subject scores with auto-grade, status tracking (Present/Absent/Deferred), teacher comments, and automatic deviation computation (score − class average)
- **Best-of-N Grading** — Configurable N-best subject scoring (0–7); picks the top N subject scores for total points
- **Batch Mark Entry** — High-performance bulk insert via SQLite transactions; Excel template with score/status/comment columns
- **Publish / Release Workflow** — Two-phase gate: teacher publishes per subject, admin releases exam
- **Analysis** — Broadsheet, subject metrics, grade distribution, most improved/dropped, merit list
- **PDF Report Cards** — A4 report cards with:
  - Subject table (score, grade, points, rank, deviation, teacher comment, grading remarks)
  - Student photo
  - Performance trend chart
  - Performance band auto-remark (High/Average/Low, configurable)
  - QR code (SHA-256 security verification)
  - Security stamp (hex digest)
  - Rubber stamp overlay
  - School logo watermark
- **School Settings** — School name, term dates, logo watermark, rubber stamp, Best-of-N value, performance band remarks
- **Role-Based Access** — Admin and Teacher roles with scoped dashboard views

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 (LTS) |
| UI Framework | JavaFX 17.0.6 |
| Database | SQLite (via JDBC) |
| PDF Generation | OpenPDF (iText fork) |
| QR Code | ZXing (core + javase) |
| Excel | Apache POI (XSSF) |
| Build Tool | Maven |

## Getting Started

### Prerequisites

- Java 17+ (JDK)
- Maven 3.8+
- Internet connection (first build only — downloads JavaFX, SQLite, and other dependencies)

### Build

```bash
mvn clean package
```

### Run

```bash
# Using Maven (preferred)
mvn javafx:run

# Using the packaged JAR directly
java -jar target/exam-analysis-2.0.0.jar
```

The database file `exam_analysis.db` is created automatically in the project root on first launch.

## Project Structure

```
src/main/java/com/zaraki/exams/
├── Launcher.java               — Bootstrap entry point
├── Main.java                   — JavaFX Application, login → dashboard
├── SeedData.java               — Demo data generator
├── auth/
│   ├── LoginForm.java
│   ├── UserManagementForm.java
│   └── PasswordUtils.java
├── config/
│   ├── SettingsManager.java    — Key-value settings (school info, Best-of-N, band remarks)
│   └── CurriculumSystem.java
├── database/
│   └── DatabaseEngine.java     — Singleton, DDL + migration, connection management
├── forms/
│   ├── AnalysisForm.java       — 5-tab analysis (broadsheet, metrics, distribution, merit, trend)
│   ├── BulkMarksForm.java      — Excel template generation + upload
│   ├── DashboardForm.java      — Role-based sidebar + navigation
│   ├── ExamForm.java           — Exam + exam_subject setup
│   ├── GradingScaleForm.java   — Subject grading scales
│   ├── MarksEntryForm.java     — Per-student marks with status, comment, deviation
│   ├── PublishForm.java        — Teacher publish / admin release workflow
│   ├── ReportForm.java         — Report card generation UI
│   ├── SchoolSettingsForm.java — School info, Best-of-N, band auto-remarks
│   ├── StudentForm.java        — Student CRUD + photo upload + subject enrollment
│   ├── SubjectAssignmentForm.java — Assign subjects to form/stream groups
│   ├── SubjectForm.java        — Subject definitions
│   └── TeacherAssignmentForm.java
├── model/                      — POJOs (Mark, Student, Subject, etc.)
├── repository/
│   └── MarksRepository.java    — Batch insert/query for marks with all columns
├── reporting/
│   └── ReportCardGenerator.java — A4 PDF: photo, deviation, QR, SHA-256 stamp, trend, auto-remark
└── service/
    ├── ExamAnalysisService.java — Grading, Best-of-N, deviation, status-aware queries
    └── ExcelService.java       — Excel templates → import (score + status + comment)
```

## License

See [LICENSE.txt](LICENSE.txt).
