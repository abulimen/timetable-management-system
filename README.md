# University Timetable Scheduling Engine

Enterprise-grade automated timetable scheduling system using **OptaPlanner** constraint solver.

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![OptaPlanner](https://img.shields.io/badge/OptaPlanner-9.44.0-orange.svg)](https://www.optaplanner.org/)
[![Angular](https://img.shields.io/badge/Angular-17-red.svg)](https://angular.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

---

## 🎯 What It Does

Automatically schedules university lessons into timeslots and rooms while respecting:

- **Hard Constraints** (must be satisfied):
  - No room double-booking
  - No lecturer conflicts
  - No student group overlaps
  - Room capacity must fit all students
  - Room must have required features (projector, lab equipment, etc.)
  - Zone restrictions (course can only be in certain buildings)
  - Lunch break protection
  - Working hours limits

- **Soft Constraints** (optimized):
  - Prefer rooms that match group size (avoid waste)
  - Balance lessons across days
  - Minimize lecturer room transitions
  - Avoid early morning (7am) classes
  - Reduce consecutive teaching hours (lecturer fatigue)

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **🌐 Online Classes** | Courses can be marked as online (no room needed, unlimited capacity) |
| **📦 Bulk Import** | Multi-step guided import from CSV files |
| **⚙️ Dynamic Settings** | Configure constraints via UI, regenerate timeslots on the fly |
| **📊 Feasibility Check** | Pre-solve validation detects impossible constraints |
| **🔍 Constraint Analysis** | Explains exactly what's violated and why |
| **📅 Semester Archives** | Archive and restore historical timetables |
| **📌 Lesson Pinning** | Lock specific lessons in place |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+ (for frontend)
- MySQL 8.0+

### 1. Clone & Configure
```bash
git clone <repository-url>
cd BUTMS

# Create database
mysql -u root -e "CREATE DATABASE timetable_db;"

# Configure (edit if needed)
# src/main/resources/application.yml
```

### 2. Run Backend
```bash
mvn spring-boot:run
```

### 3. Run Frontend
```bash
cd frontend
npm install
npm start
```

### 4. Open Application
- **Frontend:** http://localhost:4200
- **Backend API:** http://localhost:8080/api/v1

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [User Guide](docs/USER_GUIDE.md) | Step-by-step usage instructions |
| [API Reference](docs/API_REFERENCE.md) | Complete REST API documentation |
| [Architecture](docs/ARCHITECTURE.md) | System design and components |
| [Setup Guide](docs/SETUP_GUIDE.md) | Installation and configuration |
| [System Inputs](docs/SYSTEM_INPUTS.md) | Data models and entity schemas |
| [Constraints](docs/CONSTRAINTS.md) | Hard and soft constraint catalog |
| [Performance](docs/PERFORMANCE_OPTIMIZATION.md) | Scaling and optimization guide |
| [Changelog](CHANGELOG.md) | Version history |

---

## 🔌 API Overview

### Solver Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/solver/solve` | Start solver |
| `GET` | `/api/v1/solver/status` | Check solver status |
| `POST` | `/api/v1/solver/terminate` | Stop solver early |
| `GET` | `/api/v1/solver/feasibility` | Pre-solve validation |
| `GET` | `/api/v1/solver/analysis` | Constraint violations |

### Data Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/timetable` | Get generated timetable |
| `PATCH` | `/api/v1/lessons/{id}` | Update/pin lesson |
| `POST` | `/api/v1/bulk/{entity}/import` | Bulk import from CSV |
| `GET` | `/api/v1/bulk/{entity}/template` | Download CSV template |

### Settings & System
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/settings` | Get constraint settings |
| `PUT` | `/api/v1/settings/{key}` | Update setting |
| `POST` | `/api/v1/settings/regenerate-timeslots` | Regenerate timeslots |
| `POST` | `/api/v1/semesters/archive` | Archive current semester |
| `DELETE` | `/api/v1/bulk/wipe` | System-wide data wipe |

---

## 🏗️ Tech Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Spring Boot 3.2.5 |
| **Solver** | OptaPlanner 9.44.0 |
| **Frontend** | Angular 17 |
| **Database** | MySQL 8.0 |
| **Migrations** | Flyway |
| **Build** | Maven + npm |
| **Java** | 17 |

---

## 📊 Project Structure

```
BUTMS/
├── src/main/java/com/university/timetable/
│   ├── config/          # OptaPlanner configuration
│   ├── controller/      # REST API endpoints
│   ├── domain/          # JPA entities + OptaPlanner model
│   ├── dto/             # Request/Response DTOs
│   ├── exception/       # Global error handling
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # Business logic
│   └── solver/          # OptaPlanner constraint provider
├── src/main/resources/
│   ├── db/migration/    # Flyway SQL migrations (V1-V20)
│   ├── application.yml  # Spring configuration
│   └── solver-config.xml # OptaPlanner solver config
├── frontend/            # Angular 17 application
│   ├── src/app/
│   │   ├── features/    # Page components
│   │   ├── core/        # Services and shared code
│   │   └── layout/      # Sidebar, header
│   └── package.json
└── docs/                # Documentation
```

---

## 🖥️ Frontend Pages

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/dashboard` | Overview statistics |
| Zones | `/zones` | Building/area management |
| Rooms | `/rooms` | Physical spaces |
| Features | `/features` | Room capabilities |
| Lecturers | `/lecturers` | Teaching staff |
| Student Groups | `/student-groups` | Classes/cohorts |
| Courses | `/courses` | Academic subjects |
| Lessons | `/lessons` | Generated lesson slots |
| Solver | `/solver` | Run the scheduler |
| Timetable | `/timetable` | View generated schedule |
| Semesters | `/semesters` | Archive management |
| Bulk Import | `/import` | CSV import wizard |
| Settings | `/settings` | System configuration |

---

## 🔧 Configuration

### Constraint Settings (Database)

These can be modified via API or the Settings page:

| Setting | Default | Description |
|---------|---------|-------------|
| `lunch_break_start` | 12:00 | Start of lunch period |
| `lunch_break_end` | 13:00 | End of lunch period |
| `latest_end_time` | 18:00 | Latest lesson end (Mon-Thu) |
| `friday_latest_end_time` | 12:00 | Latest lesson end (Friday) |
| `max_lecturer_consecutive_hours` | 4 | Max teaching hours without break |
| `weight_early_morning` | 3 | Penalty for 7am classes |

---

## 📝 License

[MIT License](LICENSE)

---

## 🤝 Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📋 Version

Current version: **1.2.0**

See [CHANGELOG.md](CHANGELOG.md) for release history.
