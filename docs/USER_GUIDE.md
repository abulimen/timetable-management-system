# User Guide

A comprehensive guide for using the University Timetable Scheduling System.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Dashboard](#dashboard)
3. [Managing Data](#managing-data)
4. [Bulk Import](#bulk-import)
5. [Running the Solver](#running-the-solver)
6. [Viewing the Timetable](#viewing-the-timetable)
7. [Settings](#settings)
8. [Semester Management](#semester-management)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Accessing the Application

Open your web browser and navigate to:
```
http://localhost:4200
```

### Navigation

The sidebar on the left provides access to all features:

| Section | Pages |
|---------|-------|
| **Overview** | Dashboard |
| **Data** | Zones, Rooms, Features, Lecturers, Student Groups, Courses |
| **Scheduling** | Solver, Timetable, Lessons |
| **System** | Semesters, Bulk Import, Settings |

---

## Dashboard

The dashboard provides a quick overview of your data:

- **Total Courses**: Number of courses in the system
- **Total Lessons**: Generated lesson slots
- **Total Rooms**: Available scheduling rooms
- **Scheduled**: Lessons with assigned timeslots and rooms

### Key Actions from Dashboard

- Click **Go to Solver** to start generating timetables
- View summary statistics before solving

---

## Managing Data

### Zones

Zones represent buildings or campus areas.

**To add a zone:**
1. Go to **Zones** in the sidebar
2. Click **Add Zone**
3. Enter the zone name (e.g., "Engineering Building")
4. Click **Save**

**Example zones:**
- Science Complex
- Main Building
- Laboratory Wing

---

### Rooms

Rooms are physical spaces where lessons take place.

**To add a room:**
1. Go to **Rooms**
2. Click **Add Room**
3. Fill in:
   - **Name**: e.g., "Room A101"
   - **Capacity**: Maximum students
   - **Zone**: Select the building/area
   - **Features**: Check applicable features (Projector, Computers, etc.)
4. Click **Save**

**Tips:**
- Capacity is a HARD constraint – rooms must fit all students
- Features are matched against course requirements

---

### Features

Features are room capabilities that courses may require.

**Common features:**
- Projector
- Whiteboard
- Computers
- Lab Equipment
- Wet Lab

**To add a feature:**
1. Go to **Features**
2. Click **Add Feature**
3. Enter the feature name
4. Click **Save**

---

### Lecturers

**To add a lecturer:**
1. Go to **Lecturers**
2. Click **Add Lecturer**
3. Enter:
   - **Name**: Full name
   - **Email**: Unique email address
4. Click **Save**

**Setting Unavailability:**
- Click on a lecturer to view/edit unavailable times
- Add periods when the lecturer cannot teach

---

### Student Groups

Student groups represent classes or cohorts.

**To add a student group:**
1. Go to **Student Groups**
2. Click **Add Group**
3. Enter:
   - **Name**: e.g., "COSC_1A"
   - **Size**: Number of students
   - **Parent Group**: Optional (for hierarchical groups)
4. Click **Save**

**Hierarchical Groups Example:**
```
CS Year 1 (120 students) ← Parent
├── CS Group A (40)
├── CS Group B (40)
└── CS Group C (40)
```

---

### Courses

**To add a course:**
1. Go to **Courses**
2. Click **Add Course**
3. Fill in:
   - **Code**: e.g., "COSC101"
   - **Name**: e.g., "Introduction to Programming"
   - **Weekly Hours**: Total hours per week
   - **Lecturer**: Select from dropdown
   - **Student Group**: Select from dropdown
   - **🌐 Online Course**: Check if this is an online class
4. Check **Generate lessons on save** (recommended)
5. Click **Save**

**Online Courses:**
- Do not require physical rooms
- No capacity limits
- Display with 🌐 badge in the list

---

## Bulk Import

For importing large amounts of data at once, use the dedicated Bulk Import page.

### Accessing Bulk Import
Go to **Bulk Import** in the sidebar (under System).

### Import Order

Data must be imported in this order:
1. **Zones** (optional)
2. **Features** (optional)
3. **Lecturers** (required)
4. **Student Groups** (required)
5. **Rooms** (required)
6. **Courses** (required)

### How to Import

1. **Download Template**: Click the template link for each entity type
2. **Fill in Data**: Open CSV in Excel/Google Sheets and add your data
3. **Select File**: Click the file input and choose your CSV
4. **Validate**: Click "🔍 Validate Files" to check for errors
5. **Import**: Click "🚀 Start Import" to upload

### CSV Formats

#### Lecturers
```csv
name,email
John Smith,john.smith@university.edu
Jane Doe,jane.doe@university.edu
```

#### Courses
```csv
code,name,weekly_hours,lecturer_email,student_group_name,is_online
COSC101,Intro to Programming,3,john.smith@university.edu,COSC_1A,false
ONL101,Online Course,2,jane.doe@university.edu,COSC_1B,true
```

### Cross-File Validation

The system validates references between files:
- Lecturer emails in courses must exist in lecturers file
- Student groups in courses must exist in student-groups file
- Zones in rooms must exist in zones file

---

## Running the Solver

### Before Solving

1. Ensure all data is entered:
   - At least one lecturer
   - At least one student group
   - At least one room
   - At least one course with lessons

2. Check feasibility:
   - Go to **Solver** page
   - Click **Check Feasibility**
   - Review any blocking issues

### Starting the Solver

1. Go to **Solver** page
2. Review the feasibility status
3. Click **Start Solver**
4. Wait for solving to complete (status changes from "Solving" to "Idle")

### Solver Modes

| Mode | Description |
|------|-------------|
| **Full Replan** | Re-schedule all lessons from scratch |
| **Stability** | Keep pinned lessons fixed, only move unpinned |

### Understanding the Score

- `0hard/-50soft` = Valid solution (all hard constraints satisfied)
- `-3hard/-50soft` = Invalid solution (3 hard constraint violations)

**Goal:** `0hard` with lowest possible soft penalty.

---

## Viewing the Timetable

### Timetable Grid

The timetable shows lessons organized by:
- **Columns**: Days of the week (Monday-Friday)
- **Rows**: Time slots

### Lesson Cards

Each lesson card shows:
- Course code (top, bold)
- Room name OR "🌐 Online"
- Lecturer name

### Filtering

Use the filters at the top:
- **Student Group**: View one group's schedule
- **Lecturer**: View one lecturer's schedule
- **Room**: View one room's usage

### Pinning Lessons

Click on a lesson to toggle its **pinned** status:
- Pinned lessons (yellow border) won't move during solving
- Use this for fixed appointments

---

## Settings

### Accessing Settings

Go to **Settings** in the sidebar.

### Configurable Parameters

| Setting | Description |
|---------|-------------|
| `lunch_break_start` | Start of lunch period |
| `lunch_break_end` | End of lunch period |
| `latest_end_time` | Latest lesson end (Mon-Thu) |
| `friday_latest_end_time` | Latest lesson end (Friday) |
| `max_lecturer_consecutive_hours` | Max hours without break |

### Regenerating Timeslots

After changing timing settings:
1. Click **Regenerate Timeslots**
2. This creates new timeslots based on current settings
3. Re-run the solver after regenerating

### Danger Zone

⚠️ **System Wipe**: Deletes ALL data (courses, lessons, lecturers, etc.)
- Requires typing "DELETE" to confirm
- Cannot be undone!

---

## Semester Management

### Archiving a Semester

At the end of a semester:
1. Go to **Semesters**
2. Click **Archive Current Semester**
3. Enter:
   - **Code**: e.g., "2024_2025_S1"
   - **Name**: e.g., "2024/2025 First Semester"
4. Click **Archive**

This:
- Saves all current data to archive tables
- Clears main tables for new semester
- Preserves rooms, zones, and features

### Viewing Archives

1. Go to **Semesters**
2. Click on an archived semester
3. View the historical timetable

---

## Troubleshooting

### "No valid solution found"

1. Check feasibility: **Solver → Check Feasibility**
2. Common issues:
   - Group too large for any room
   - Course requires features no room has
   - Not enough room-slots for all lessons

### "Lessons not showing on timetable"

1. Ensure lessons are generated:
   - Go to **Lessons** page
   - Check if lessons exist
2. Run the solver to assign timeslots

### "Import failed"

1. Check CSV format matches template
2. Ensure required columns are present
3. Check for duplicate codes/emails
4. Verify referenced data exists (lecturers before courses)

### "Settings not taking effect"

1. Click **Regenerate Timeslots** after timing changes
2. Re-run the solver

---

## Quick Reference

### Keyboard Shortcuts

(Coming soon)

### Common Workflows

#### Starting Fresh
1. Settings → Danger Zone → Wipe All Data
2. Bulk Import → Upload all CSVs
3. Solver → Start Solver

#### Weekly Update
1. Add/modify courses as needed
2. Solver → Start Solver (Stability mode to preserve pins)

#### End of Semester
1. Semesters → Archive Current Semester
2. Import new semester data
3. Generate new timetable

---

## Getting Help

For technical issues:
- Check [SETUP_GUIDE.md](SETUP_GUIDE.md)
- Review [API_REFERENCE.md](API_REFERENCE.md)
- See [CONSTRAINTS.md](CONSTRAINTS.md) for constraint details
