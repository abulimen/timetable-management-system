# Developer Handoff Document

**Babcock University Timetable Management System (BUTMS)**

**Version:** 1.2.0  
**Handoff Date:** 2026-01-08  
**Completion Status:** ~85%

---

## 1. Executive Summary

The Babcock University Timetable Management System (BUTMS) is an enterprise-grade automated timetable scheduling system using **OptaPlanner** constraint solver. The system automatically schedules university lessons into timeslots and rooms while respecting a comprehensive set of hard and soft constraints.

### What This System Does

- **Automated Scheduling**: Uses AI-powered constraint satisfaction to generate optimal timetables
- **Bulk Data Management**: Import courses, lecturers, rooms, and student groups via CSV
- **Real-time Constraint Analysis**: Explains why schedules succeed or fail
- **Semester Management**: Archive and restore historical timetables
- **Role-Based Access Control**: Complete authentication and authorization system

---

## 2. Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Backend Framework** | Spring Boot | 3.2.5 |
| **Scheduling Engine** | OptaPlanner | 9.44.0 |
| **Frontend Framework** | Angular | 17 |
| **Database** | MySQL | 8.0 |
| **ORM** | Hibernate | 6.4.4 |
| **Migrations** | Flyway | Spring Boot Default |
| **Java** | OpenJDK | 17 |
| **Build (Backend)** | Maven | 3.8+ |
| **Build (Frontend)** | npm | 18+ |

---

## 3. Project Structure

```
BUTMS/
├── src/main/java/com/university/timetable/
│   ├── TimetableApplication.java     # Spring Boot entry point
│   ├── config/                        # OptaPlanner configuration
│   │   ├── OptaPlannerConfig.java
│   │   └── WebConfig.java
│   ├── controller/                    # 20 REST API controllers
│   │   ├── AuthController.java        # Authentication endpoints
│   │   ├── UserController.java        # User management
│   │   ├── SolverController.java      # Solver operations
│   │   ├── TimetableController.java   # Timetable retrieval
│   │   ├── CourseController.java      # Course CRUD
│   │   ├── LecturerController.java    # Lecturer CRUD
│   │   ├── RoomController.java        # Room CRUD
│   │   ├── StudentGroupController.java
│   │   ├── SettingsController.java
│   │   ├── BulkOperationsController.java
│   │   ├── ExportController.java      # PDF/Excel export
│   │   ├── AvailabilityChangeRequestController.java
│   │   └── ... (20 total)
│   ├── domain/                        # 17 JPA entities
│   │   ├── Lesson.java               # OptaPlanner @PlanningEntity
│   │   ├── Timeslot.java             # @PlanningVariable
│   │   ├── Room.java
│   │   ├── Course.java
│   │   ├── Lecturer.java
│   │   ├── StudentGroup.java
│   │   ├── User.java                 # Authentication entity
│   │   ├── RefreshToken.java         # JWT refresh tokens
│   │   └── ... (17 total)
│   ├── dto/                          # 19 Data Transfer Objects
│   ├── exception/                    # Global error handling
│   ├── repository/                   # 14 Spring Data JPA repos
│   ├── security/                     # Authentication layer
│   │   ├── SecurityConfig.java       # Spring Security config
│   │   ├── JwtService.java           # JWT token management
│   │   ├── JwtAuthenticationFilter.java
│   │   └── UserDetailsServiceImpl.java
│   ├── service/                      # 18 business logic services
│   │   ├── AuthService.java          # Login/logout/refresh
│   │   ├── UserService.java          # User management
│   │   ├── SolverService.java        # OptaPlanner solving
│   │   ├── InfeasibilityChecker.java # Pre-solve validation
│   │   ├── ConstraintJustificationService.java
│   │   ├── BulkImportService.java    # CSV import
│   │   ├── ExportService.java        # PDF/Excel export
│   │   └── ... (18 total)
│   ├── solver/                       # OptaPlanner constraints
│   │   └── TimetableConstraintProvider.java
│   └── util/
├── src/main/resources/
│   ├── db/migration/                 # Flyway SQL migrations V1-V25
│   ├── application.yml               # Spring configuration
│   └── solver-config.xml             # OptaPlanner solver config
├── frontend/                         # Angular 17 application
│   ├── src/app/
│   │   ├── features/                 # 17 feature modules
│   │   │   ├── login/               # Login page
│   │   │   ├── dashboard/           # Overview statistics
│   │   │   ├── users/               # User management (admin)
│   │   │   ├── courses/             # Course management
│   │   │   ├── lecturers/           # Lecturer management
│   │   │   ├── rooms/               # Room management
│   │   │   ├── student-groups/      # Student group management
│   │   │   ├── zones/               # Zone management
│   │   │   ├── features/            # Room features
│   │   │   ├── lessons/             # Lesson management
│   │   │   ├── solver/              # Solver control panel
│   │   │   ├── timetable/           # Timetable view
│   │   │   ├── import/              # Bulk CSV import wizard
│   │   │   ├── export/              # Export functionality
│   │   │   ├── settings/            # System settings
│   │   │   ├── semesters/           # Semester archives
│   │   │   └── special-events/      # Special events
│   │   ├── core/
│   │   │   ├── services/
│   │   │   │   ├── api.service.ts   # HTTP client wrapper
│   │   │   │   └── auth.service.ts  # Authentication service
│   │   │   ├── guards/
│   │   │   │   └── auth.guard.ts    # Route protection
│   │   │   ├── interceptors/        # HTTP interceptors
│   │   │   └── models/
│   │   ├── layout/                  # Sidebar, header
│   │   └── shared/                  # Shared components
│   └── package.json
├── docs/                            # Documentation (10 files)
│   ├── API_REFERENCE.md             # Complete REST API docs
│   ├── ARCHITECTURE.md              # System design
│   ├── SETUP_GUIDE.md               # Installation guide
│   ├── USER_GUIDE.md                # End-user documentation
│   ├── CONSTRAINTS.md               # Constraint catalog
│   ├── USER_AUTH_REQUIREMENTS.md    # Auth requirements spec
│   └── ... (10 total)
├── pom.xml                          # Maven configuration
├── README.md                        # Quick start guide
├── CHANGELOG.md                     # Version history
└── ROADMAP.md                       # Project roadmap
```

---

## 4. What's Already Completed

### ✅ Phase 1: Core Foundation (100%)

| Feature | Status | Details |
|---------|--------|---------|
| Spring Boot Backend | ✅ Complete | v3.2.5, RESTful API |
| MySQL Database | ✅ Complete | Flyway migrations V1-V25 |
| OptaPlanner Integration | ✅ Complete | v9.44.0, constraint solver |
| Entity Models | ✅ Complete | 17 JPA entities |
| CRUD APIs | ✅ Complete | All entities have full endpoints |

### ✅ Phase 2: Scheduling Engine (100%)

| Feature | Status | Details |
|---------|--------|---------|
| 10 Hard Constraints | ✅ Complete | Room conflict, lecturer conflict, capacity, etc. |
| 6 Soft Constraints | ✅ Complete | Day balance, fatigue, transitions |
| Feasibility Check | ✅ Complete | Pre-solve validation |
| Constraint Analysis | ✅ Complete | Explains violations |
| Solving Modes | ✅ Complete | FULL_REPLAN, STABILITY |
| Lesson Pinning | ✅ Complete | Lock lessons in place |

### ✅ Phase 3: Data Management (100%)

| Feature | Status | Details |
|---------|--------|---------|
| Bulk CSV Import | ✅ Complete | Multi-step workflow with validation |
| CSV Templates | ✅ Complete | Downloadable for each entity |
| Cross-file Validation | ✅ Complete | Validates references between files |
| Online Classes | ✅ Complete | No room required, unlimited capacity |
| Semester Archiving | ✅ Complete | Archive, restore, view history |
| System Wipe | ✅ Complete | Clear all data with confirmation |

### ✅ Phase 4: Frontend UI (100%)

| Feature | Status | Details |
|---------|--------|---------|
| Angular 17 SPA | ✅ Complete | Standalone components |
| All Entity Pages | ✅ Complete | 17 feature modules |
| Timetable Grid | ✅ Complete | Interactive with filters |
| Solver Control | ✅ Complete | Start, stop, status |
| Settings Page | ✅ Complete | Configure constraints |
| Import Wizard | ✅ Complete | Guided bulk import |
| Dark Mode | ✅ Complete | System preference support |

### ✅ Phase 5: User Authentication (100%)

| Feature | Status | Details |
|---------|--------|---------|
| JWT Authentication | ✅ Complete | Access + refresh tokens |
| User Management | ✅ Complete | CRUD for users (admin) |
| Role-Based Access | ✅ Complete | 5 roles: SUPER_ADMIN, ADMIN, COORDINATOR, LECTURER, VIEWER |
| Login/Logout | ✅ Complete | Full flow with token management |
| Account Lockout | ✅ Complete | 5 failed attempts → 30 min lock |
| Password Security | ✅ Complete | BCrypt hashing |
| Availability Change Requests | ✅ Complete | Approval workflow for lecturers |

### ✅ Phase 6: Documentation (100%)

| Document | Location | Purpose |
|----------|----------|---------|
| [README.md](file:///home/x/Projects/BUTMS/README.md) | Root | Quick start guide |
| [CHANGELOG.md](file:///home/x/Projects/BUTMS/CHANGELOG.md) | Root | Version history |
| [CONTRIBUTING.md](file:///home/x/Projects/BUTMS/CONTRIBUTING.md) | Root | Contribution guidelines |
| [API_REFERENCE.md](file:///home/x/Projects/BUTMS/docs/API_REFERENCE.md) | docs/ | Complete REST API docs |
| [USER_GUIDE.md](file:///home/x/Projects/BUTMS/docs/USER_GUIDE.md) | docs/ | End-user documentation |
| [SETUP_GUIDE.md](file:///home/x/Projects/BUTMS/docs/SETUP_GUIDE.md) | docs/ | Installation guide |
| [ARCHITECTURE.md](file:///home/x/Projects/BUTMS/docs/ARCHITECTURE.md) | docs/ | System design |
| [CONSTRAINTS.md](file:///home/x/Projects/BUTMS/docs/CONSTRAINTS.md) | docs/ | Constraint catalog |
| [USER_AUTH_REQUIREMENTS.md](file:///home/x/Projects/BUTMS/docs/USER_AUTH_REQUIREMENTS.md) | docs/ | Auth requirements spec |

---

## 5. What Remains to Be Done

> [!IMPORTANT]
> The following features are planned but **NOT yet implemented**. This is where the new developer should focus.

### 🔶 Phase 7: Export Features (Priority: HIGH)

| Feature | Priority | Effort | Description |
|---------|----------|--------|-------------|
| **PDF Export** | HIGH | Low | Export timetables as PDF documents for printing |
| **Excel Export** | HIGH | Low | Export timetables as Excel spreadsheets |
| **iCal Export** | MEDIUM | Low | Export schedules in iCal format for calendar apps |

**Implementation Notes:**
- `ExportService.java` exists but may need completion
- `ExportController.java` has endpoints defined
- Consider using Apache PDFBox or iText for PDF generation
- Use Apache POI (already in dependencies) for Excel

**Files to modify:**
- [ExportService.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/service/ExportService.java) (23KB - likely has partial implementation)
- [ExportController.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/controller/ExportController.java)
- Frontend: `frontend/src/app/features/export/`

---

### 🔶 Phase 8: Audit Logging (Priority: MEDIUM)

| Feature | Priority | Effort | Description |
|---------|----------|--------|-------------|
| **Audit Trail** | MEDIUM | Medium | Log all data changes with actor, timestamp, old/new values |
| **Audit Log Viewer** | MEDIUM | Medium | Admin UI to view and search audit logs |

**Implementation Notes:**
- Requirements documented in [AUDIT_LOGGING_REQUIREMENTS.md](file:///home/x/Projects/BUTMS/docs/AUDIT_LOGGING_REQUIREMENTS.md)
- Consider using Spring Data Envers or a custom solution
- Audit availability changes are particularly important (see safeguards in auth requirements)

**Suggested approach:**
1. Create `AuditLog` entity with columns: id, entityType, entityId, action, actor, timestamp, previousValue, newValue
2. Create `AuditLogRepository` and `AuditLogService`
3. Use Spring AOP or JPA listeners (`@EntityListeners`) to intercept changes
4. Add Flyway migration for `audit_logs` table

---

### 🔶 Phase 9: DevOps & Deployment (Priority: HIGH)

| Feature | Priority | Effort | Description |
|---------|----------|--------|-------------|
| **Dockerfile** | HIGH | Low | Containerize the backend application |
| **Docker Compose** | HIGH | Low | Full stack setup (backend + frontend + MySQL) |
| **CI/CD Pipeline** | MEDIUM | Medium | GitHub Actions or similar for automated builds |
| **Environment Configs** | MEDIUM | Low | Separate configs for dev/staging/production |
| **Health Endpoints** | LOW | Low | Spring Actuator health checks |

**Suggested Docker setup:**

```dockerfile
# Dockerfile (Backend)
FROM eclipse-temurin:17-jdk as build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: timetable_db
      MYSQL_ROOT_PASSWORD: password
    ports:
      - "3306:3306"
  
  backend:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - db
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/timetable_db
  
  frontend:
    build: ./frontend
    ports:
      - "4200:80"
    depends_on:
      - backend
```

---

### 🔶 Phase 10: Testing (Priority: MEDIUM)

| Feature | Status | Priority | Effort |
|---------|--------|----------|--------|
| Unit Tests (Backend) | 🟡 Partial | MEDIUM | Medium |
| Integration Tests | ❌ Not Started | MEDIUM | High |
| E2E Tests (Frontend) | ❌ Not Started | LOW | High |
| Performance Tests | ❌ Not Started | LOW | Medium |

**Testing commands:**
```bash
# Backend tests
mvn test

# Frontend tests
cd frontend && npm test
```

**Suggested testing approach:**
1. Add JUnit 5 tests for services (especially `SolverService`, `AuthService`)
2. Add `@SpringBootTest` integration tests for controllers
3. Use Cypress or Playwright for E2E frontend tests
4. Use JMeter for solver performance testing

---

### 🔶 Phase 11: Nice-to-Have Features (Priority: LOW)

| Feature | Priority | Effort | Description |
|---------|----------|--------|-------------|
| **Email Notifications** | LOW | Medium | Notify users of schedule changes |
| **Mobile Responsive** | LOW | Medium | Better mobile experience |
| **Bulk Lesson Edit** | LOW | Medium | Edit multiple lessons at once |
| **Drag-and-Drop** | LOW | High | Drag lessons on the timetable grid |
| **Multi-language (i18n)** | LOW | High | Support for multiple languages |
| **Student Portal View** | LOW | Medium | Read-only view for students |

---

## 6. Database Schema

### Current Migrations (V1-V25)

| Migration | Description |
|-----------|-------------|
| V1 | Base tables (zone, feature, room) |
| V2 | Lecturer tables with unavailabilities |
| V3 | Student group with hierarchy |
| V4 | Course tables with features/zones |
| V5 | Timetable tables (timeslot, lesson) |
| V6 | Sample data for testing |
| V7 | Constraint settings table |
| V8 | Course-StudentGroup many-to-many |
| V9 | Semester archive metadata |
| V10-V16 | Babcock mock data and fixes |
| V17 | Friday end time setting |
| V18 | Early morning weight setting |
| V19 | Lecturer consecutive hours setting |
| V20 | Online course flag |
| V21-V22 | Special events |
| V23 | **Users table** (authentication) |
| V24 | **Refresh tokens table** (JWT) |
| V25 | **Availability change requests** |

**Location:** [/src/main/resources/db/migration/](file:///home/x/Projects/BUTMS/src/main/resources/db/migration/)

---

## 7. API Endpoints Overview

### Authentication (`/api/auth`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/login` | Authenticate user, get tokens |
| POST | `/refresh` | Refresh access token |
| POST | `/logout` | Invalidate refresh token |
| GET | `/me` | Get current user info |

### Solver Operations (`/api/v1/solver`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/solve` | Start solver |
| GET | `/status` | Check solver status |
| POST | `/terminate` | Stop solver early |
| GET | `/feasibility` | Pre-solve validation |
| GET | `/analysis` | Constraint violations |

### Data Management
| Method | Base Path | Description |
|--------|-----------|-------------|
| CRUD | `/api/v1/courses` | Course management |
| CRUD | `/api/v1/lecturers` | Lecturer management |
| CRUD | `/api/v1/rooms` | Room management |
| CRUD | `/api/v1/student-groups` | Student group management |
| CRUD | `/api/v1/zones` | Zone management |
| CRUD | `/api/v1/features` | Room features |
| CRUD | `/api/v1/users` | User management (admin) |

Full API documentation: [API_REFERENCE.md](file:///home/x/Projects/BUTMS/docs/API_REFERENCE.md)

---

## 8. Running the Project

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

### Backend Setup
```bash
# 1. Create database
mysql -u root -e "CREATE DATABASE timetable_db;"

# 2. Configure database (edit if needed)
# src/main/resources/application.yml

# 3. Run backend
mvn spring-boot:run

# Backend runs at http://localhost:8080
```

### Frontend Setup
```bash
# 1. Navigate to frontend
cd frontend

# 2. Install dependencies
npm install

# 3. Start development server
npm start

# Frontend runs at http://localhost:4200
```

### Default Login Credentials
| Email | Password | Role |
|-------|----------|------|
| `admin@babcock.edu.ng` | `Admin@123` | SUPER_ADMIN |

> [!CAUTION]
> Change the default admin password immediately in production!

---

## 9. User Roles & Permissions

### Role Hierarchy
```
SUPER_ADMIN (Full System Control)
    └── ADMIN (System Management)
         └── COORDINATOR (Timetable Management) 
              └── LECTURER (View + Limited Edit)
                   └── VIEWER (Read-Only)
```

### Permission Summary
| Feature | SUPER_ADMIN | ADMIN | COORDINATOR | LECTURER | VIEWER |
|---------|:-----------:|:-----:|:-----------:|:--------:|:------:|
| User Management | ✓ | ✓ | ✗ | ✗ | ✗ |
| Create/Edit Data | ✓ | ✓ | ✓ | ✗ | ✗ |
| Run Solver | ✓ | ✓ | ✓ | ✗ | ✗ |
| View Timetable | ✓ | ✓ | ✓ | ✓ | ✓ |
| System Settings | ✓ | ✓ | ✗ | ✗ | ✗ |
| Data Wipe | ✓ | ✗ | ✗ | ✗ | ✗ |

Full permissions matrix: [USER_AUTH_REQUIREMENTS.md](file:///home/x/Projects/BUTMS/docs/USER_AUTH_REQUIREMENTS.md)

---

## 10. Key Constraints

### Hard Constraints (Must Satisfy)
1. **Room Conflict** - No double-booking rooms
2. **Lecturer Conflict** - No overlapping lecturer assignments
3. **Student Group Conflict** - No overlapping student group lessons
4. **Room Capacity** - Room must fit all students
5. **Room Features** - Room must have required features
6. **Zone Restriction** - Course must be in allowed zone
7. **Lecturer Unavailability** - Respect lecturer blocked times
8. **Lunch Break** - No lessons during 12:00-13:00
9. **Working Hours** - Lessons must end by configured time
10. **Friday End Time** - Separate Friday end time

### Soft Constraints (Optimized)
1. **Room Efficiency** - Prefer rooms matching group size
2. **Day Balance** - Balance lessons across days
3. **Lecturer Transitions** - Minimize room changes
4. **Early Morning Penalty** - Avoid 7am classes
5. **Lecturer Fatigue** - Reduce consecutive teaching hours

Full constraint documentation: [CONSTRAINTS.md](file:///home/x/Projects/BUTMS/docs/CONSTRAINTS.md)

---

## 11. Important Files for New Developer

### Must-Read Documentation
1. [README.md](file:///home/x/Projects/BUTMS/README.md) - Project overview
2. [ARCHITECTURE.md](file:///home/x/Projects/BUTMS/docs/ARCHITECTURE.md) - System design
3. [USER_AUTH_REQUIREMENTS.md](file:///home/x/Projects/BUTMS/docs/USER_AUTH_REQUIREMENTS.md) - Auth spec (especially availability safeguards)
4. [ROADMAP.md](file:///home/x/Projects/BUTMS/ROADMAP.md) - Current progress

### Key Backend Files
- [TimetableConstraintProvider.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/solver/TimetableConstraintProvider.java) - All scheduling constraints
- [SecurityConfig.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/security/SecurityConfig.java) - Spring Security setup
- [AuthService.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/service/AuthService.java) - Authentication logic
- [SolverService.java](file:///home/x/Projects/BUTMS/src/main/java/com/university/timetable/service/SolverService.java) - OptaPlanner integration

### Key Frontend Files
- [auth.service.ts](file:///home/x/Projects/BUTMS/frontend/src/app/core/services/auth.service.ts) - Frontend auth
- [auth.guard.ts](file:///home/x/Projects/BUTMS/frontend/src/app/core/guards/auth.guard.ts) - Route protection
- [app.routes.ts](file:///home/x/Projects/BUTMS/frontend/src/app/app.routes.ts) - All routes

---

## 12. Development Workflow

### Adding a New Feature
1. Create Flyway migration for database changes
2. Add JPA entity in `domain/`
3. Create repository in `repository/`
4. Implement service in `service/`
5. Create controller in `controller/`
6. Add frontend feature module in `frontend/src/app/features/`
7. Update routes in `app.routes.ts`
8. Add to navigation sidebar

### Making Database Changes
1. Create new migration file: `V26__description.sql`
2. Never modify existing migrations (Flyway checksums)
3. Run `mvn spring-boot:run` to apply automatically

### Testing Changes
```bash
# Backend
mvn test

# Frontend
cd frontend && npm test

# Full application
mvn spring-boot:run  # Terminal 1
cd frontend && npm start  # Terminal 2
# Open http://localhost:4200
```

---

## 13. Known Issues & Gotchas

> [!WARNING]
> Pay attention to these common pitfalls.

1. **Run Maven from project root** - Not from `frontend/` directory
   ```bash
   cd /home/x/Projects/BUTMS
   mvn spring-boot:run
   ```

2. **Flyway migrations are one-way** - Never edit existing V*.sql files

3. **OptaPlanner solving is async** - Use `/solver/status` to check progress

4. **JWT tokens expire** - Access: 15 min, Refresh: 7 days. Frontend must handle refresh.

5. **CORS is configured** - Check `WebConfig.java` if adding new origins

6. **Availability safeguards** - Lecturers can abuse availability settings. The system has deadline-based controls and approval workflows documented in [USER_AUTH_REQUIREMENTS.md](file:///home/x/Projects/BUTMS/docs/USER_AUTH_REQUIREMENTS.md#34-lecturer-availability-safeguards)

---

## 14. Contact & Resources

### Project Links
- **Repository**: Local at `/home/x/Projects/BUTMS`
- **Documentation**: `/docs/` directory

### External Resources
- [Spring Boot Docs](https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/)
- [OptaPlanner Docs](https://www.optaplanner.org/docs/optaplanner/latest/optaplanner-docs.html)
- [Angular 17 Docs](https://angular.io/docs)
- [Flyway Migrations](https://flywaydb.org/documentation/)

---

## 15. Summary: What to Work On Next

### Immediate Priorities (Week 1-2)
1. [ ] **PDF/Excel Export** - Complete export functionality
2. [ ] **Docker Setup** - Create Dockerfile and docker-compose.yml
3. [ ] **Environment Configs** - Separate dev/staging/prod configurations

### Short-Term (Week 3-4)
4. [ ] **Audit Logging** - Implement audit trail for data changes
5. [ ] **CI/CD Pipeline** - Set up GitHub Actions for automated builds
6. [ ] **Backend Unit Tests** - Add tests for critical services

### Medium-Term (Month 2)
7. [ ] **iCal Export** - Calendar integration
8. [ ] **Integration Tests** - API-level testing
9. [ ] **Email Notifications** - Optional, based on requirements

---

**Document prepared for developer handoff. Good luck with the project! 🚀**
