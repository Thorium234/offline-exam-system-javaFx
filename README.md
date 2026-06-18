# Zaraki Exam Analysis System

A lightweight, offline-first desktop GUI application for analyzing exam results and generating report forms in Kenyan secondary schools.

Built with **Java 17+, JavaFX, SQLite, and Maven**.

## Features

- **Student Management** — Register and view students across forms (1–4) and streams
- **Subject Configuration** — Define subjects with departments and groupings (Compulsory/Elective)
- **Exam Setup** — Academic year, term, and exam series management
- **Marks Entry** — Enter scores per exam with a cross-join grid of students and subjects
- **Batch Mark Entry** — High-performance bulk insert via SQLite transactions
- **Dashboard** — Overview counts of students, subjects, exams, and marks
- **PDF Report Forms** — *(coming soon)* Generate A4 report cards with subject breakdown and positions

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
java -jar target/exam-analysis-1.0.0.jar
```

The database file `exam_analysis.db` is created automatically in the project root on first launch.

## Project Structure

```
src/main/java/com/zaraki/exams/
├── database/
│   └── DatabaseEngine.java     — Schema bootstrap & connection management
├── model/
│   ├── Exam.java
│   ├── GradingScale.java
│   ├── Mark.java
│   ├── Student.java
│   └── Subject.java
├── repository/
│   └── MarksRepository.java    — Batch mark entry & data access
├── Launcher.java               — Entry point (calls Main.launch)
└── Main.java                   — JavaFX Application with full GUI
```

## License

See [LICENCE.txt](LICENCE.txt).
