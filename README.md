# Zaraki Exam Analysis System

A lightweight, offline-first desktop application for analyzing exam results and generating report forms in Kenyan secondary schools.

Built with **Java 17+, SQLite, and Maven**.

## Features

- **Student Management** — Register and manage students across forms (1–4) and streams
- **Subject Configuration** — Define subjects with departments and groupings (Compulsory/Elective)
- **Grading Scales** — Configurable grade boundaries with points and remarks
- **Exam Setup** — Academic year, term, and exam series management
- **Batch Mark Entry** — High-performance bulk insert of exam scores using SQLite transactions
- **Analysis Engine** — Multi-threaded computation of subject/student metrics, mean scores, standard deviation, and dense rankings
- **PDF Report Forms** — Generate A4 report cards with subject breakdown, positions, and performance trends

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 (LTS) |
| UI | Console / JavaFX (planned) |
| Database | SQLite (via JDBC) |
| Build Tool | Maven |

## Getting Started

### Prerequisites

- Java 17+ (JDK)
- Maven 3.8+

### Build & Run

```bash
# Build with dependencies bundled
mvn clean package

# Run
java -jar target/exam-analysis-1.0.0.jar
```

The application will create `exam_analysis.db` in the project root on first launch.

### Running with JavaFX (requires internet)

To use the JavaFX GUI, add the following dependencies to `pom.xml` and run:

```bash
mvn javafx:run
```

See [JavaFX + Maven setup](https://openjfx.io/openjfx-docs/#maven) for details.

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
└── Main.java                   — Application entry point (console)
```

## License

See [LICENCE.txt](LICENCE.txt).
