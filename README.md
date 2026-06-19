# Thorium Exam Analysis System v2

A lightweight, offline-first desktop GUI application for analyzing exam results and generating professional A4 report cards in Kenyan secondary schools.

Built with **Java 17+, JavaFX, SQLite, OpenPDF, and Maven**.

## Features

- **Student Management** — Register and view students across forms (1–4) and streams
- **Subject Configuration** — Define subjects with departments and groupings (Compulsory/Elective)
- **Exam Setup** — Academic year, term, and exam series management
- **Marks Entry** — Enter scores per exam with auto-grade, or bulk upload via Excel
- **Batch Mark Entry** — High-performance bulk insert via SQLite transactions
- **Publish / Release Workflow** — Two-phase gate: teacher publishes per subject, admin releases exam
- **Analysis** — Broadsheet, subject metrics, grade distribution, most improved/dropped, merit list
- **PDF Report Cards** — A4 report cards with subject tables, performance trend chart, and rubber stamp
- **School Settings** — Configure school name, term dates, logo watermark, and stamp

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 (LTS) |
| UI Framework | JavaFX 17.0.6 |
| Database | SQLite (via JDBC) |
| Build Tool | Maven |

## Getting Started

### Prerequisites

- Java 17+ (JDK)
- Maven 3.8+
- Internet connection (first build only — downloads JavaFX and SQLite dependencies)

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
│   ├── SettingsManager.java
│   └── CurriculumSystem.java
├── database/
│   └── DatabaseEngine.java     — Singleton, DDL, connection management
├── forms/
│   ├── AnalysisForm.java       — 5-tab analysis
│   ├── BulkMarksForm.java
│   ├── DashboardForm.java
│   ├── ExamForm.java
│   ├── GradingScaleForm.java
│   ├── MarksEntryForm.java
│   ├── PublishForm.java
│   ├── ReportForm.java
│   ├── SchoolSettingsForm.java
│   ├── StudentForm.java
│   ├── SubjectForm.java
│   └── TeacherAssignmentForm.java
├── model/                      — 5 POJOs
├── repository/
│   └── MarksRepository.java
├── reporting/
│   └── ReportCardGenerator.java
└── service/
    ├── ExamAnalysisService.java
    └── ExcelService.java
```

## License

See [LICENCE.txt](LICENCE.txt).
