# Thorium Exam Analysis System v2 — System Report

## 1. Overview

A desktop exam analysis & report card generation system for Kenyan secondary schools.
Offline-first, JavaFX-based GUI with SQLite persistence. Generates professional
A4 report cards with subject tables, performance trends, and merit lists.

**Version:** 2.1.0  
**Java:** 17+  
**Build:** Maven (`mvn clean package`) → fat shaded JAR  

---

## 2. Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| UI | JavaFX 17.0.6 + CSS | All screens, tables, charts |
| Database | SQLite (WAL mode) | Single file `exam_analysis.db` |
| PDF | OpenPDF 1.3.30 | Report cards, merit lists, student lists |
| Excel | Apache POI 5.3.0 | Template generation, bulk import |
| Build | Maven + shade plugin | Fat JAR at `target/exam-analysis-2.0.0.jar` |

**Threading:** All heavy work (PDF gen, Excel parse, ranking compute) runs via
`javafx.concurrent.Task< >` off the JavaFX Application Thread. ProgressIndicator
shown during processing.

**Entry point:** `com.zaraki.exams.Launcher` → `Main.java`

---

## 3. Directory Structure

```
src/main/java/com/zaraki/exams/
├── Launcher.java          — Bootstrap
├── Main.java              — JavaFX Application, login → dashboard flow
├── SeedData.java          — Demo data generator
├── auth/
│   ├── LoginForm.java
│   ├── UserManagementForm.java
│   └── PasswordUtils.java
├── config/
│   ├── SettingsManager.java
│   └── CurriculumSystem.java    (enum: 8-4-4 / CBC)
├── database/
│   └── DatabaseEngine.java      (singleton, DDL, connection)
├── forms/
│   ├── AnalysisForm.java        (5-tab analysis)
│   ├── BulkMarksForm.java
│   ├── DashboardForm.java       (sidebar nav, stats)
│   ├── ExamForm.java
│   ├── GradingScaleForm.java
│   ├── MarksEntryForm.java
│   ├── PublishForm.java         (publish workflow, multi-sheet excel)
│   ├── ReportForm.java          (report card preview + PDF gen)
│   ├── SchoolSettingsForm.java
│   ├── StudentForm.java
│   ├── SubjectForm.java
│   └── TeacherAssignmentForm.java
├── model/                       (5 POJOs: Exam, Student, Subject, Mark, GradingScale)
├── repository/
│   └── MarksRepository.java
├── reporting/
│   └── ReportCardGenerator.java
└── service/
    ├── ExamAnalysisService.java (rankings, metrics, grades)
    └── ExcelService.java        (POI templates, import)
```

---

## 4. Database Schema (9 tables)

All created in `DatabaseEngine.executeDDL()`.

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `students` | id, admission_number (UNIQUE), full_name, form(1-4), stream, status | Indexed on admission, (form,stream) |
| `subjects` | id, subject_code (UNIQUE), subject_name, department, grouping(Compulsory/Elective) | |
| `exams` | id, academic_year, term(Term 1/2/3), exam_series, released, released_by, released_at | released column added by migration |
| `grading_scales` | id, subject_id (nullable=global), minimum_mark, maximum_mark, grade, points, remarks | Per-subject or global fallback |
| `marks` | exam_id, student_id, subject_id (composite PK), score, grade_achieved, points_achieved | Indexed on exam, student |
| `users` | id, username(UNIQUE), password_hash, salt, full_name, role(admin/teacher) | Admin seeded by default |
| `exam_subjects` | id, exam_id, subject_id, out_of(default 100), uploaded_by/at, published, published_by/at | Links exam→subject with publish state |
| `teacher_subjects` | id, user_id, subject_id, form, stream | Controls teacher subject access |
| `app_settings` | key(TEXT PK), value(TEXT) | school_name, logo_path, curriculum, etc. |

**Foreign keys:** marks → exams/students/subjects, exam_subjects/teacher_subjects → exams/subjects/users

---

## 5. Current Features

### 5.1 Authentication & Users
- Login with username/password (SHA-256 + salt)
- Two roles: **admin** (full access) and **teacher** (limited to assigned subjects)
- Admin can manage users via sidebar → **Users** (CRUD table)
- Default admin: username `admin`, password `admin`

### 5.2 Data Management
- **Students:** Add individually, bulk via Excel template, seed demo data (20/stream × 4 forms × 2 streams)
- **Subjects:** CRUD with code, name, department, compulsory/elective flag
- **Exams:** Create by year + term + series
- **Grading Scales:** Per-subject or global; auto-generate from curriculum preset (8-4-4: A=12pts → E=1pt; CBC: 4-tier)
- **Curriculum switcher:** Sidebar dropdown toggles between 8-4-4 and CBC (presets change grading tables)

### 5.3 Marks Entry
- **Manual entry:** Sidebar → **Marks Entry** → select exam + form + stream + subject → editable score table with auto-grade/points on cell edit
- **Excel bulk:** Sidebar → **Bulk Marks** → generate class template → upload filled file
- **Per-subject upload (via Publish):** Each subject uploadable individually with inline table or Excel

### 5.4 Publish / Release Workflow
- **Publish** sidebar item → select exam → table of all subjects
- Each subject shows: upload status, marks count, publish status, editable "Out Of" column
- **Upload:** Click subject → inline editable student table + "Upload Excel" + "Download Template"
- **Teacher filter:** Non-admin teachers only see subjects assigned to them via Teacher Assignment
- **Publish:** Teacher clicks "Publish" after uploading to lock subject
- **Release:** Admin clicks "Release Exam" (orange button) — requires ALL subjects published
- Reports & Analysis blocked until exam is released

### 5.5 Teacher Subject Assignment
- Sidebar → **Teacher Subjects** → select teacher → add/remove subject+form+stream combos
- Controls which subjects appear in Publish for teachers

### 5.6 Multi-Sheet Excel Upload (Admin)
- In Publish, admin can upload one `.xlsx` with multiple sheets
- Sheet naming convention: `"SubjectName - FormXStream"` (e.g. `"Chemistry - Form 1A"`)
- System parses sheet name, resolves subject + class, imports all sheets in one pass
- Supports single-sheet-per-subject as well

### 5.7 Teacher Template Generation
- In Publish, admin selects teacher → downloads Excel with one sheet per assigned subject
- Each sheet pre-populated with students for that form+stream
- Naming matches the multi-sheet upload parser

### 5.8 Analysis (5 tabs)
Accessed via sidebar → **Analysis**. Requires exam to be released.

| Tab | Content |
|-----|---------|
| Broadsheet | Class rankings with positions, prev points, delta |
| Subject Metrics | Mean score, grade, std dev, rank, candidates per subject |
| Grade Distribution | Per-subject histogram of grade counts |
| Most Improved/Dropped | Two-exam comparison with position changes |
| Merit List | Per-subject score/deviation/position columns, export to PDF |

### 5.9 Reports
Sidebar → **Reports**. Requires exam to be released.

- **Single student:** Search by admission or name → preview report card on screen → Generate PDF
- **Bulk (stream/form):** Select stream or form → single multi-page PDF, each student on own A4

### 5.10 PDF Report Card Layout (OpenPDF)
Each A4 page contains:
1. **Header:** School logo (background watermark, 8% opacity centered), school name (from settings), term dates, exam info
2. **Student info:** Admission, name, form, stream
3. **Subject table:** Subject, Score, Grade, Points, Position, Remarks
4. **Summary:** Total Marks, Total Points, Mean Points
5. **Performance indicator:** "Improved by X pts" / "Dropped by X pts" / "First exam"
6. **Trend chart:** SVG-like line chart with labeled axes, legend
7. **Rubber stamp:** (optional) bottom-right placement

### 5.11 School Settings
Sidebar → **Settings** → configure:
- School name (used in PDF headers)
- Term opening/closing dates
- School logo image (becomes watermark on reports)
- Rubber stamp image (placed bottom-right on PDF)

---

## 6. Key Design Decisions

- **Offline-first:** No network required. Single SQLite file `exam_analysis.db`.
- **Threading model:** `javafx.concurrent.Task` for all heavy work. Never block the JavaFX Application Thread.
- **Grade calculation:** `determineGradeAndPoints(score, subjectId, examId)` normalizes by `out_of` from `exam_subjects`. Grading scales store percentage ranges (0-100). Default `out_of=100` ensures backward compatibility.
- **Optional subjects:** Only students with a `marks` row for a subject are counted in that subject's metrics. Merit list shows "-" for un-taken subjects. Totals count only taken subjects.
- **Publish workflow:** Two-phase gate — teacher uploads+publishes per subject, admin releases entire exam.
- **School logo as watermark:** Uses `PdfPageEvent` with `getDirectContentUnder()` and 8% opacity via `PdfGState.setFillOpacity()`.
- **User search:** Free-text `TextField` with SQL `LIKE` (no dropdown). NB: `LIKE '%…%'` does a full table scan — not recommended for very large datasets.
- **Bulk report PDF:** Single `Document` with `doc.newPage()` between students — same layout as single-student PDF.

---

## 7. Navigation Flow

```
Main.start()
  └─→ showLogin()  →  LoginForm  →  DashboardForm(loggedInUser, loggedInUsername, loggedInRole, logoutCallback)
                                     └─→ Sidebar (14 items):
                                         ├── Dashboard        — stats cards, trend chart, demo tools
                                         ├── Students         — CRUD + Excel import
                                         ├── Subjects         — CRUD
                                         ├── Exams            — CRUD
                                         ├── Grading Scales   — manage grade tables
                                         ├── Users            — manage admin/teacher accounts
                                         ├── Teacher Subjects — assign subjects to teachers
                                         ├── Settings         — school info, logo, stamp
                                         ├── Publish          — upload marks, publish, release
                                         ├── Marks Entry      — manual per-class marks entry
                                         ├── Bulk Marks       — Excel template + upload
                                         ├── Analysis         — 5-tab analysis (released exams only)
                                         └── Reports          — preview + PDF (released exams only)
```

---

## 8. Security Model

| Action | Admin | Teacher |
|--------|-------|---------|
| Manage users, exams, subjects, grading | ✅ | ❌ |
| Upload marks (all subjects) | ✅ | ✅ (assigned only) |
| Publish subjects | ✅ | ✅ (own only) |
| Release exam | ✅ | ❌ |
| View analysis / generate reports | ✅ | ✅ (released only) |
| View/assign teacher subjects | ✅ | ❌ |
| School settings | ✅ | ❌ |

---

## 9. Known Limitations & Future Work

### High Priority
- **Zero test coverage:** No unit/integration tests for ranking, grading, PDF gen, or Excel parsing.
- **Thread-unsafe connection:** `getConnection()` has no synchronization; concurrent Tasks can cause "database is locked" errors.
- **Weak password hashing:** Single-round SHA-256 (now upgraded to PBKDF2WithHmacSHA256 in v2.1).
- **Student subject registration:** Need a table to register which subjects each student takes (especially for electives like Computer Studies). Currently the system infers from presence of marks, but this causes issues for report cards showing subjects the student doesn't take.
- **Per-student subject list:** Report card should list only subjects the student is registered for, not all subjects with marks.
- **Form/Stream subject assignment:** Need to specify which subjects are offered to which forms/streams (e.g. Physics only in Form 3-4).

### Medium Priority
- **Backup/Restore:** No built-in database backup/restore functionality.
- **Student promotions:** No bulk promotion of students to next form at year-end.
- **Transcript generation:** Cumulative report spanning multiple terms/years.
- **Subject rank improvements:** Show subject rank as fraction (e.g. `5/40`) in all views.
- **Password change:** Users can't change their own password from within the app.
- **Report card template editor:** Allow customizing PDF layout (colors, fonts, sections) from UI.
- **DB queries on FX thread:** Multiple forms load data on the JavaFX Application Thread (DashboardForm, MarksEntryForm, ReportForm, etc.).
- **Duplicate merit list logic:** The same algorithm appears in both `AnalysisForm` and `ReportCardGenerator` (~300 lines duplicated).

### Low Priority  
- **Print directly from app:** Currently saves PDF to file only.
- **Email reports:** Send PDF report cards via email.
- **Multi-language support:** UI hardcoded in English.
- **Dark mode:** No theme toggle.
- **Analytics dashboard:** Trend charts currently per-student; no aggregate school-wide trends.
- **REST API:** No network API; would require architectural changes.
- **Cloud sync:** Would require conflict resolution for offline-first model.
- **No pagination on student search:** Search limited to 20 results with no "next page" control.
- **UI allows creating exams without subjects:** No validation that subjects exist before exam creation.

---

## 10. Build & Run

```bash
# Compile
mvn clean compile

# Package fat JAR
mvn clean package -DskipTests

# Run
java -jar target/exam-analysis-2.1.0.jar

# The JAR is self-contained (shades all dependencies including JavaFX,
# SQLite, OpenPDF, Apache POI)
```

---

## Changelog

### v2.1.0 (2026-06-19)
- **Security:** Replaced SHA-256 password hashing with PBKDF2WithHmacSHA256 (600K iterations)
- **Security:** Added column whitelist validation to prevent SQL injection via filter column names
- **Bugfix:** Fixed thread-unsafe database connection — `getConnection()` now properly synchronized
- **Bugfix:** Fixed mutable static PDF watermark causing corrupted images in concurrent PDF generation
- **Bugfix:** Removed duplicate `INSERT_BATCH_SQL` string; single insert now delegates to batch insert
- **Bugfix:** File upload now validates image extensions before saving
- **Quality:** Added `equals()` / `hashCode()` to all 5 POJO models
- **Quality:** Added JUnit 5 + Mockito test infrastructure with unit tests for PasswordUtils, DatabaseEngine, ExamAnalysisService
- **Quality:** Removed dead code (unused `Label` in GradingScaleForm.refresh())
- **Docs:** Updated README.md with accurate version, features, and project structure
- **Docs:** Corrected REPORT.md inaccuracies (table count 7→9, LIKE performance caveat, version)
- **Chore:** Updated `.gitignore` to exclude generated PDFs, school assets, and dependency-reduced-pom.xml

*Generated: 2026-06-19 — Update this document when adding major features.*
