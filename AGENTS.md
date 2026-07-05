# Thorium Exam Analysis System — AGENTS.md

> Desktop GUI for analyzing Kenyan secondary school exam results and generating A4 report cards.

## Build & Run Commands

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ExamAnalysisServiceTest

# Run specific test method
mvn test -Dtest=ExamAnalysisServiceTest#computeSubjectMetrics_withData

# Package fat JAR (shaded with all deps)
mvn clean package

# Run via Maven (preferred)
mvn javafx:run

# Run the fat JAR directly
java -jar target/exam-analysis-2.1.0.jar

# Checkstyle (warnings only, doesn't fail build)
mvn checkstyle:check

# SpotBugs (doesn't fail build)
mvn spotbugs:check
```

Tests use **in-memory SQLite** (`jdbc:sqlite:`) — data is per-class, isolated. No test shares state across classes.

## Quick Reference — What You Need to Know

### Database Engine — **Non-Obvious Gotchas**

File: `src/main/java/com/zaraki/exams/database/DatabaseEngine.java`

- **Singleton via double-checked locking** (`volatile instance` + `synchronized` block)
- **ThreadLocal connections**: each thread gets its own `Connection`. Do NOT close connections explicitly — they live for the thread's lifetime.
- **Dead connection handling**: `getConnection()` checks `isClosed()` and creates a new one if dead.
- **Shutdown hook**: closes the shutdown hook thread's connection at JVM exit.
- **DB path resolution**: checks system property `exam.db.path` → fallback to `exam_system.properties` file → fallback to `exam_analysis.db` in project root.
- **`validateFilterColumn(col)`**: whitelist-based SQL injection prevention for dynamic `WHERE` clauses. Allowed: `form`, `stream`, `status`, `department`, `grouping`, `term`, `exam_series`, `""`. Always use this for any user-driven column input.
- **Schema versioning**: No migration framework. Columns are added via `ALTER TABLE ADD COLUMN` inside `try/catch` blocks that silently ignore "duplicate column" errors.

### SQLite JDBC — `rs.getBlob()` is NOT supported

Always use `rs.getBytes("column_name")`. `rs.getBlob()` throws `SQLFeatureNotSupportedException`. Already fixed in `ReportCardGenerator`.

### Marks Batch Insert

File: `MarksRepositoryImpl.java`

Uses `conn.setAutoCommit(false)` + batch execute (500 at a time) + manual commit. Transaction is rolled back on failure. `conn.setAutoCommit(true)` restored in `finally` block.

### SettingsManager — **Do NOT Cache/Reuse**

Constructor calls `ensureTable()` which runs DDL every time. Always `new SettingsManager()` on demand — it's lightweight.

### Logging

- Custom `LoggerUtil` in `util/` package with file + console handlers.
- Use `LoggerUtil.info()`, `LoggerUtil.warn()`, `LoggerUtil.severe()`, `LoggerUtil.fine()`.
- SLF4J warning about missing `StaticLoggerBinder` is harmless (Apache POI pulls it as transitive dep).

### UI Patterns

- All forms return their root `Node` via a `getView()` method (not `getRoot()`). The `DashboardForm` orchestrates them in a `StackPane` content area.
- Background work (PDF gen, Excel gen, upload) runs in `javafx.concurrent.Task<Void>` or `Task<ImportResult>` on background threads.
- UI updates go through `Platform.runLater()`.
- Each background thread gets its own DB connection (ThreadLocal).
- Error dialogs use: `ErrorHandler.showError()`, `ErrorHandler.showInfo()`, `ErrorHandler.showConfirm()`, `UIUtils.showError()`, `UIUtils.showInfo()`.

### AppTheme

File: `forms/AppTheme.java` — color constants for light and dark mode. Dark mode colors: `DARK_BG=#1a1d23`, `DARK_CARD=#242730`, `DARK_TEXT=#e0e0e0`. Sidebar icon constants use emoji strings.

## Application Architecture

```
Launcher.java → Main.java (JavaFX Application)
                 ├── LoginForm.java → SessionManager (auth)
                 └── DashboardForm.java (sidebar navigation hub)
                      ├── NavBar (left sidebar, role-filtered items)
                      ├── TopBar (user info, dark mode toggle, logout)
                      ├── StatCards (summary counts)
                      ├── DemoDataPanel (seed/destroy demo data)
                      └── Content stack (swaps forms in/out via format):
                           Students  → StudentForm / StudentBrowserForm
                           Subjects  → SubjectForm / SubjectAssignmentForm
                           Exams     → ExamForm
                           Marks     → MarksEntryForm / BulkMarksForm
                           Grading   → GradingScaleForm
                           Publish   → PublishForm (two-phase)
                           Analysis  → AnalysisForm (5 tabs: Broadsheet, Metrics, Grade Dist, Merit, Trend, Weak Areas)
                           Reports   → ReportForm
                           Streams   → StreamManagementForm
                           Teachers  → TeacherAssignmentForm / TeacherDashboardForm
                           Users     → UserManagementForm (admin only)
                           Recycle   → RecycleBinForm
                           Settings  → SchoolSettingsForm
```

### Class Groups

```
com.zaraki.exams/
├── Launcher.java          — Bootstrap, calls Main.main()
├── Main.java              — JavaFX Application, login → dashboard
├── SeedData.java          — Demo data for Kenyan schools
├── auth/                  — LoginForm, PasswordUtils (PBKDF2), SessionManager, UserManagementForm
├── config/                — SettingsManager (key-value CRUD), CurriculumSystem (844/CBC enum)
├── database/              — DatabaseEngine (singleton, ThreadLocal), DatabaseBackupService
├── forms/                 — 18+ form classes + AppTheme, Dashboard* components, EmptyStatePlaceholder, Publish* panels, Analysis* tabs
├── model/                 — POJOs: Student, Subject, Exam, Mark, GradingScale
├── repository/            — Interface (I*) + Impl for each entity: Student, Subject, Exam, Marks, Stream, GradingScale, Settings, TeacherSubject, User
├── reporting/             — ReportCardGenerator (OpenPDF, QR, stamp, watermark, trend chart)
├── service/               — Interface (I*) + Impl: ExamAnalysisServiceImpl (grading, ranking, merit), ExcelServiceImpl (templates, upload)
└── util/                  — LoggerUtil, UIUtils, ValidationUtils, ErrorHandler, CacheService (TTL-based ConcurrentHashMap)
```

## Database Schema

All tables auto-created with `CREATE TABLE IF NOT EXISTS`. Migrations via `ALTER TABLE ADD COLUMN` in try/catch.

| Table | Key Columns | Notes |
|---|---|---|
| `users` | id, username, password_hash, salt, full_name, role | Admin seeded at startup; password force-reset if doesn't match "admin" |
| `students` | id, admission_number UNIQUE, full_name, form(1-4), stream, deallocated(0/1), photo BLOB | photo via `rs.getBytes()` NOT `getBlob()` |
| `subjects` | id, subject_code UNIQUE, subject_name, department, grouping(Compulsory/Elective) | |
| `exams` | id, academic_year, term(Term1/2/3), exam_series, released(0/1), released_by, released_at | |
| `exam_subjects` | id, exam_id FK, subject_id FK, out_of(100), published(0/1), uploaded_by/at | UNIQUE(exam_id, subject_id) |
| `marks` | exam_id FK, student_id FK, subject_id FK, score REAL, grade_achieved, points_achieved, status(P/A/D), teacher_comment, teacher_name, deviation REAL | PK(exam_id, student_id, subject_id) |
| `stream_subjects` | id, form, stream, subject_id FK | UNIQUE(form, stream, subject_id) |
| `student_subjects` | student_id FK, subject_id FK | PK(student_id, subject_id) |
| `streams` | id, form, stream | UNIQUE(form, stream). Populated by migration from students table. |
| `grading_scales` | id, subject_id(NULL=default), minimum_mark, maximum_mark, grade, points, remarks | |
| `teacher_subjects` | id, user_id FK, subject_id FK, form, stream | UNIQUE(user_id, subject_id, form, stream) |
| `app_settings` | key TEXT PK, value TEXT | Keys: school_name, opening_date, closing_date, logo_path, stamp_path, best_of_n, remark_high/average/low, curriculum, dark_mode |

## Grading System

### 8-4-4 Grade Scale (default)

| Grade | Points | Grade | Points |
|-------|--------|-------|--------|
| A | 12 | C+ | 7 |
| A- | 11 | C | 6 |
| B+ | 10 | C- | 5 |
| B | 9 | D+ | 4 |
| B- | 8 | D | 3 |

Also D- (2), E (1). Determined by `ExamAnalysisServiceImpl.determineGradeAndPoints()`. Mean grade via `meanPointsToGrade()`.

### CBC Grade Scale

EE (4), ME (3), AE (2), BE (1). Controlled by `CurriculumSystem` enum.

### Best-of-N Grading

Setting `best_of_n` in `app_settings` (0-7). Picks top N subject point values. When 0, uses all subjects.

## Test Patterns

All repository/service tests extend `DatabaseTestBase` which:
1. Registers `InMemoryDbExtension` JUnit 5 Extension (resets singleton via reflection, uses `jdbc:sqlite:` in-memory)
2. Calls `cleanAllTables()` in `@BeforeEach`
3. Provides helper methods: `insertSubject()`, `insertStudent()`, `insertExam()`, `insertMark()`, `insertGradeScale()`, `insertUser()`, `insertStream()`, `insertExamSubject()`, `insertStudentSubject()`, `insertStreamSubject()`, `insertTeacherSubject()`

**Critical**: `InMemoryDbExtension` uses reflection to reset `DatabaseEngine`'s private static fields (`instance`, `dbUrl`, `connectionHolder`). If the DatabaseEngine singleton structure changes, tests will break silently.

## Naming & Style

- **Repository pattern**: `IStudentRepository` (interface) → `StudentRepositoryImpl` (implementation). Same for services.
- **Java conventions**: camelCase methods, PascalCase classes, 4-space indentation (Checkstyle-enforced).
- **Table columns**: `snake_case` in SQL → `camelCase` Java property names via `PropertyValueFactory`.
- **Forms**: Named `*Form.java`. Each has a `getView()` that returns the root `Node`.
- **Model POJOs**: `equals()` and `hashCode()` use `Objects.equals()` + `Objects.hash()` with `instanceof` pattern matching (Java 17+).
- **Validation**: `ValidationUtils.requireNonEmpty()`, `requireInRange()`, `validateAdmissionNumber()`.
- **CSS resource**: Loaded at `Main.java:48`: `/styles/application.css`.

## Code Conventions — Non-Obvious

1. **Repository methods return `Map<String, Object>`** — not model POJOs (except `MarksRepositoryImpl` which returns `Mark` model). The map keys are the SQL column names. This is the dominant pattern throughout the codebase.
2. **`UIUtils` creates static repository instances** (`examRepo`, `streamRepo`) — these live for the app lifetime.
3. **`Comment` column in marks** is a single text field per mark row, not per student.
4. **Photo storage**: BLOB column, stored as raw bytes, returned as `byte[]`.
5. **Student status**: Uses `deallocated` boolean flag (0/1) for soft-delete, plus `status` column for Active/Inactive.
6. **Empty states**: Some forms don't handle empty data gracefully (e.g., AnalysisForm with no exams). `EmptyStatePlaceholder` class exists but isn't universally applied.
7. **Excel templates use `Form 1 East` style** for stream+form display (space between form number and stream name).

## Key Classes Quick Reference

| Class | Lines | Purpose |
|---|---|---|
| `ReportCardGenerator` | 822+ | OpenPDF: photo, deviation, QR, SHA-256 stamp, trend chart, auto-remark, watermark |
| `ExamAnalysisServiceImpl` | 400+ | Grading, Best-of-N, deviation, ranking, merit, trend, comparison |
| `ExcelServiceImpl` | 500+ | Excel templates, upload processing, multi-sheet teacher flow |
| `DatabaseEngine` | 357 | Singleton, DDL, ThreadLocal connections, migration, filter validation |
| `SettingsManager` | 52 | Key-value accessor — always `new`, don't cache |
| `MarksRepositoryImpl` | 136 | Batch insert (500/chunk), transaction rollback |
| `SeedData` | 200+ | 40 students/form, 12 subjects, default grades, Kenyan names |
| `CacheService` | 44 | TTL-based in-memory cache (default 300s) |
| `CurriculumSystem` | 53 | 8-4-4 and CBC grade presets |
| `PasswordUtils` | 44 | PBKDF2WithHmacSHA256, 600K iterations |
| `DatabaseBackupService` | 86 | File copy backups to `backups/` directory |

## Forms with Restructured Components

These forms were split from monolithic classes into focused components:

**AnalysisForm**: Thin orchestrator → `AnalysisBroadsheetTab`, `AnalysisSubjectMetricsTab`, `AnalysisGradeDistTab`, `AnalysisMeritListTab`, `AnalysisComparisonTab`, `AnalysisTrendTab`, `AnalysisWeakAreasTab`, `AnalysisDashboardTab`

**PublishForm**: Orchestrator → `PublishTeacherPanel` (upload phase), `PublishAdminPanel` (release phase), `PublishScoreTable` (editable table)

**DashboardForm**: Lazy-loaded form container → `DashboardTopBar`, `DashboardNavBar`, `DashboardStatCards`, `DashboardDemoDataPanel`

## Two-Phase Publish Workflow

1. **Teacher publishes per subject**: Teacher uploads marks for their assigned subject/form/stream. Sets `published=1` on `exam_subjects`.
2. **Admin releases exam**: Admin reviews published subjects, then releases the entire exam. Sets `released=1` on `exams` table.

After release, marks become read-only for teachers. Admin can release/unrelease.

## Important Gotchas

- **Statement pointer closed errors**: When using multiple `PreparedStatement`s from the same connection simultaneously, ensure inner `ResultSet`s are closed before the outer PreparedStatement is reused.
- **Query parameters use `ps.setObject()` for nullable ints**: See `MarksRepositoryImpl.java:47` — `ps.setObject(6, mark.getPointsAchieved() > 0 ? mark.getPointsAchieved() : null)`.
- **Deviation field**: Uses `ps.setObject(10, mark.getDeviation(), java.sql.Types.REAL)` — not `ps.setDouble()` — to allow null.
- **`exam_analysis.db` in `.gitignore`**: Database file is never committed. Fresh installs start with admin/admin login (seeded by DatabaseEngine DDL).
- **Checkstyle runs at `verify` phase only**: `mvn verify` will run checkstyle + spotbugs but won't fail on violations (`failsOnError=false`).
- **The `teacher_dashboard` form** is separate from the main admin dashboard — teachers get a filtered view.
