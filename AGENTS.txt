# Thorium Exam Analysis System — AI Agent Handoff Report

## Session State

- Tests: **18/18 passing** (14 original + 4 PDF generation tests)
- Build: `mvn clean package` produces fat JAR at `target/exam-analysis-2.1.0.jar`
- Database: SQLite file `exam_analysis.db` created auto at project root

## Architecture

```
Launcher.java → Main.java (JavaFX Application)
                 ├── LoginForm.java → SessionManager (auth)
                 └── DashboardForm.java (sidebar navigation hub)
                      ├── StudentForm / SubjectForm / ExamForm / MarksEntryForm
                      ├── BulkMarksForm (Excel import/export)
                      ├── ReportForm → ReportCardGenerator (PDF)
                      ├── AnalysisForm → ExamAnalysisService
                      ├── PublishForm (two-phase release)
                      ├── SchoolSettingsForm → SettingsManager
                      └── ... (17 total forms)
```

## Database Schema (auto-created by DatabaseEngine.executeDDL)

All tables created with `CREATE TABLE IF NOT EXISTS`. Migrations via `ALTER TABLE ADD COLUMN` in try/catch.

| Table | Key Columns | Notes |
|---|---|---|
| `users` | id, username, password_hash, salt, full_name, role | Admin seeded at startup; password force-reset if doesn't match "admin" |
| `students` | id, admission_number UNIQUE, full_name, form(1-4), stream, deallocated(0/1), photo BLOB | photo stored as raw bytes (not Blob — SQLite JDBC doesn't support getBlob()) |
| `subjects` | id, subject_code UNIQUE, subject_name, department, grouping(Compulsory/Elective) | |
| `exams` | id, academic_year, term(Term1/2/3), exam_series, released(0/1), released_by, released_at | |
| `exam_subjects` | id, exam_id FK, subject_id FK, out_of(100), published(0/1), uploaded_by/at | UNIQUE(exam_id, subject_id) |
| `marks` | exam_id FK, student_id FK, subject_id FK, score REAL, grade_achieved, points_achieved, status(P/A/D), teacher_comment, teacher_name, deviation REAL | PK(exam_id, student_id, subject_id) |
| `stream_subjects` | id, form, stream, subject_id FK | UNIQUE(form, stream, subject_id) |
| `student_subjects` | student_id FK, subject_id FK | PK(student_id, subject_id) |
| `streams` | id, form, stream | UNIQUE(form, stream). Populated by migration from students table. |
| `grading_scales` | id, subject_id(NULL=default), minimum_mark, maximum_mark, grade, points, remarks | |
| `teacher_subjects` | id, user_id FK, subject_id FK, form, stream | UNIQUE(user_id, subject_id, form, stream) |
| `app_settings` | key TEXT PK, value TEXT | Keys: school_name, opening_date, closing_date, logo_path, stamp_path, best_of_n, remark_high/average/low, curriculum |

## DatabaseEngine — Critical Details

**File:** `src/main/java/com/zaraki/exams/database/DatabaseEngine.java`

- **Singleton:** `getInstance()` with double-checked locking (`synchronized` block)
- **Connection:** `ThreadLocal<Connection>` — each thread gets its *own* connection. Do NOT close connections explicitly; they live for the thread's lifetime.
- **Dead connection handling:** `getConnection()` checks `isClosed()` and creates a new one if dead.
- **Shutdown hook:** closes the shutdown hook thread's connection at JVM exit.
- **Filter column validation:** `validateFilterColumn()` — whitelist of `{"form", "stream", ""}`. Always use this for any `WHERE` clause constructed from user input (SQL injection prevention).

## Critical Code Patterns & Traps

### 1. rs.getBlob() NOT supported by SQLite JDBC
Always use `rs.getBytes("column_name")` instead. `rs.getBlob()` throws `SQLFeatureNotSupportedException`.
- Fix applied in `ReportCardGenerator.java:181` (student photo)

### 2. Statement pointer closed errors
Always wrap ResultSet in try-with-resources. When using multiple PreparedStatements from the same connection simultaneously, ensure inner ResultSets are closed before the outer PreparedStatement is reused.
- Fix applied in `ExamAnalysisService.autoGradeExam()` 

### 3. SettingsManager
- Constructor calls `ensureTable()` which runs DDL every time. Creates instance each time needed.
- Do NOT cache/reuse — always `new SettingsManager()` on demand. (Lightweight; no perf issue.)

### 4. Logging
- `util.LoggerUtil` — custom logger with file + console handlers.
- Log messages use `LoggerUtil.info()`, `LoggerUtil.warn()`, `LoggerUtil.error()`.
- SLF4J warning about missing StaticLoggerBinder is harmless (POI pulls it as transitive dep).

### 5. MarksRepository.batchInsert()
- Uses `conn.setAutoCommit(false)` + batch execute + manual commit. Transaction is rolled back on failure.
- Calls `conn.setAutoCommit(true)` in finally block.
- Max batch size: 500.

### 6. Multi-threading in forms
- Background work (PDF gen, Excel gen, upload) runs in `Task<Void>` or `Task<ImportResult>` on background threads.
- UI updates go through `Platform.runLater()`.
- Each background thread gets its own DB connection (ThreadLocal).

## Key Classes — Quick Reference

### Reporting
- `ReportCardGenerator.java` (822 lines) — PDF generation. Methods: `generateStudentReport()`, `generateBulkStudentReports()`, `generateGroupReport()`, `generateStudentListPdf()`.
  - Sub-methods: `addHeader()`, `addStudentInfo()`, `addSubjectTable()`, `addSummary()`, `addPerformanceIndicator()`, `addTrendChart()`, `addStamp()`, `addFooterSecurity()`.
  - Static block loads logo bytes for watermark.

### Service
- `ExamAnalysisService.java` — `computeSubjectMetrics()`, `computeClassRankings()`, `determineGradeAndPoints()`, `computeMeritReport()`, `computeStudentTrend()`, `normalizeByOutOf()`, `findPreviousExam()`.
- `ExcelService.java` — `generateTemplate()`, `generateSubjectTemplate()`, `processUpload()`, `processSubjectUpload()`, `generateTeacherMultiSheetTemplate()`, `processTeacherMultiSheetUpload()`.

### Forms
- `BulkMarksForm.java` — Teacher/admin bulk marks Excel flow. Teacher mode: select exam→subject→form→stream. Has "Generate All Templates" + "Upload All Sheets" for multi-sheet teacher flow.
- `ReportForm.java` — Preview (JavaFX) and PDF generation. Calls `ReportCardGenerator` on background thread.
- `PublishForm.java` — `isExamReleased(examId)` — checks `released` flag on exams table.
- `SubjectAssignmentForm.java` — Stream dropdown loaded from `streams` table, filtered by selected form via `loadStreams()`.

### Config
- `SettingsManager.java` — Key-value app_settings CRUD. `getSchoolName()`, `getOpeningDate()`, `getClosingDate()`, `getLogoPath()`, `getStampPath()`, `getSetting(key, default)`.

## Grading System

### Grade-to-Points Mapping (default KCSE)
| Grade | Points |
|-------|--------|
| A | 12 |
| A- | 11 |
| B+ | 10 |
| B | 9 |
| B- | 8 |
| C+ | 7 |
| C | 6 |
| C- | 5 |
| D+ | 4 |
| D | 3 |
| D- | 2 |
| E | 1 |

Determined by `determineGradeAndPoints(score, subjectId, examId)` in `ExamAnalysisService`.
For overall mean grade: `meanPointsToGrade(meanPoints)` in `ReportCardGenerator.java:286`.

### Best-of-N Grading
Setting `best_of_n` in `app_settings` (0-7). Picks the top N subject point values. When 0, uses all subjects.

## Build & Test

```bash
# Compile
mvn compile

# Run tests (18 total)
mvn test

# Run app
mvn javafx:run

# Package fat JAR
mvn clean package

# Run JAR directly
java -jar target/exam-analysis-2.1.0.jar
```

**Note:** Tests use the *same* `exam_analysis.db` in project root. They share state. The first test class to run seeds the database. Test order within a class matters — use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` when tests depend on state.

## Completed Fixes (this session)

1. **PDF generation crash** — `rs.getBlob("photo")` not supported by SQLite JDBC. Changed to `rs.getBytes("photo")` in `ReportCardGenerator.addStudentInfo()`.
2. **Stream dropdown in SubjectAssignmentForm** — Now loads from `streams` table, filtered by form.
3. **Excel templates simplified** — `Adm | Name | {Subject} Marks` pattern (no Status/Cmt sub-columns).
4. **Merit list cleaned** — Removed `#` column, per-subject Dev → overall Dev, reordered columns.
5. **Multi-sheet bulk marks for teachers** — New methods `generateTeacherMultiSheetTemplate()` and `processTeacherMultiSheetUpload()` in `ExcelService`. UI buttons "Generate All Templates" / "Upload All Sheets" in `BulkMarksForm`.

## Pending / Future Work

1. **Dashboard counts may be stale** — refresh on navigation or add auto-refresh timer.
2. **Photo upload validation** — no file size/type restriction currently.
3. **PrintService** — not yet integrated with ReportForm UI.
4. **exam_analysis.db file path** — hardcoded as `jdbc:sqlite:exam_analysis.db` in `DatabaseEngine.java:15`. Consider making configurable.
5. **Empty states** — some forms show nothing or crash when no data exists (e.g., AnalysisForm with no exams). Add graceful empty messages.

## File Index (all source files)

```
src/main/java/com/zaraki/exams/
├── Launcher.java
├── Main.java
├── SeedData.java
├── auth/
│   ├── LoginForm.java
│   ├── PasswordUtils.java
│   ├── SessionManager.java
│   └── UserManagementForm.java
├── config/
│   ├── AppConfig.java
│   ├── CurriculumSystem.java
│   └── SettingsManager.java
├── database/
│   ├── DatabaseEngine.java
│   └── DatabaseMigration.java
├── forms/
│   ├── AnalysisForm.java
│   ├── BulkMarksForm.java
│   ├── DashboardForm.java
│   ├── ExamForm.java
│   ├── GradingScaleForm.java
│   ├── MarksEntryForm.java
│   ├── PublishForm.java
│   ├── ReportForm.java
│   ├── SchoolSettingsForm.java
│   ├── StreamManagementForm.java
│   ├── StudentBrowserForm.java
│   ├── StudentForm.java
│   ├── SubjectAssignmentForm.java
│   ├── SubjectForm.java
│   ├── TeacherAssignmentForm.java
│   ├── TeacherDashboardForm.java
│   └── RecycleBinForm.java
├── model/
│   ├── Mark.java
│   ├── Student.java
│   ├── Subject.java
│   └── Exam.java
├── reporting/
│   └── ReportCardGenerator.java
├── repository/
│   └── MarksRepository.java
├── service/
│   ├── DevExamService.java
│   ├── ExamAnalysisService.java
│   ├── ExcelService.java
│   ├── MarksService.java
│   └── PrintService.java
└── util/
    ├── LoggerUtil.java
    └── ValidationUtil.java

src/main/resources/styles/
└── application.css

src/test/java/com/zaraki/exams/
├── auth/PasswordUtilsTest.java
├── database/DatabaseEngineTest.java
├── reporting/ReportCardGeneratorTest.java
└── service/ExamAnalysisServiceTest.java
```
