# Project Roadmap

Track the development progress of the University Timetable Scheduling Engine.

**Last Updated:** 2025-12-25

---

## Overall Completion: 85%

```
████████████████░░░░ 85%
```

---

## ✅ Phase 1: Core Foundation (Complete)

| Feature | Status | Notes |
|---------|--------|-------|
| Spring Boot Backend | ✅ Done | v3.2.5, RESTful API |
| MySQL Database | ✅ Done | Flyway migrations V1-V20 |
| OptaPlanner Integration | ✅ Done | v9.44.0, constraint solver |
| Entity Models | ✅ Done | Course, Lesson, Room, Lecturer, StudentGroup, Zone, Feature |
| CRUD APIs | ✅ Done | All entities have full endpoints |

---

## ✅ Phase 2: Scheduling Engine (Complete)

| Feature | Status | Notes |
|---------|--------|-------|
| 10 Hard Constraints | ✅ Done | Room conflict, lecturer conflict, etc. |
| 6 Soft Constraints | ✅ Done | Day balance, fatigue, transitions |
| Feasibility Check | ✅ Done | Pre-solve validation |
| Constraint Analysis | ✅ Done | Explains violations |
| Solving Modes | ✅ Done | FULL_REPLAN, STABILITY |
| Lesson Pinning | ✅ Done | Lock lessons in place |

---

## ✅ Phase 3: Data Management (Complete)

| Feature | Status | Notes |
|---------|--------|-------|
| Bulk CSV Import | ✅ Done | Multi-step workflow with validation |
| CSV Templates | ✅ Done | Downloadable for each entity |
| Cross-file Validation | ✅ Done | Validates references between files |
| Online Classes | ✅ Done | No room required, unlimited capacity |
| Semester Archiving | ✅ Done | Archive, restore, view history |
| System Wipe | ✅ Done | Clear all data with confirmation |

---

## ✅ Phase 4: Frontend UI (Complete)

| Feature | Status | Notes |
|---------|--------|-------|
| Angular 17 SPA | ✅ Done | Standalone components |
| All Entity Pages | ✅ Done | Zones, Rooms, Features, Lecturers, StudentGroups, Courses |
| Timetable Grid | ✅ Done | Interactive with filters |
| Solver Control | ✅ Done | Start, stop, status |
| Settings Page | ✅ Done | Configure constraints |
| Import Wizard | ✅ Done | Guided bulk import |
| Dark Mode | ✅ Done | System preference support |

---

## ✅ Phase 5: Documentation (Complete)

| Document | Status | Location |
|----------|--------|----------|
| README.md | ✅ Done | Root |
| CHANGELOG.md | ✅ Done | Root |
| CONTRIBUTING.md | ✅ Done | Root |
| API_REFERENCE.md | ✅ Done | /docs |
| USER_GUIDE.md | ✅ Done | /docs |
| SETUP_GUIDE.md | ✅ Done | /docs |
| FRONTEND.md | ✅ Done | /docs |
| CONSTRAINTS.md | ✅ Done | /docs |
| ARCHITECTURE.md | ✅ Done | /docs |

---

## 🟡 Phase 6: Enterprise Features (Planned)

| Feature | Status | Priority | Effort |
|---------|--------|----------|--------|
| User Authentication | ❌ Not Started | High | Medium |
| Role-Based Access | ❌ Not Started | High | Medium |
| PDF Export | ❌ Not Started | High | Low |
| Excel Export | ❌ Not Started | High | Low |
| iCal Export | ❌ Not Started | Medium | Low |
| Audit Logging | ❌ Not Started | Medium | Medium |
| Bulk Lesson Edit | ❌ Not Started | Medium | Medium |
| Email Notifications | ❌ Not Started | Low | Medium |
| Mobile Responsive | ❌ Not Started | Low | Medium |

---

## 🟡 Phase 7: DevOps & Deployment (Planned)

| Feature | Status | Priority | Effort |
|---------|--------|----------|--------|
| Dockerfile | ❌ Not Started | High | Low |
| Docker Compose | ❌ Not Started | High | Low |
| CI/CD Pipeline | ❌ Not Started | Medium | Medium |
| Environment Configs | ❌ Not Started | Medium | Low |
| Health Endpoints | ❌ Not Started | Low | Low |

---

## 🟡 Phase 8: Testing (Partial)

| Feature | Status | Priority | Effort |
|---------|--------|----------|--------|
| Unit Tests (Backend) | 🟡 Partial | Medium | Medium |
| Integration Tests | ❌ Not Started | Medium | High |
| E2E Tests (Frontend) | ❌ Not Started | Low | High |
| Performance Tests | ❌ Not Started | Low | Medium |

---

## Quick Commands

### Start Development
```bash
# Backend
mvn spring-boot:run

# Frontend
cd frontend && npm start
```

### Run Tests
```bash
# Backend
mvn test

# Frontend
cd frontend && npm test
```

---

## Feature Requests / Ideas

_Add future ideas here:_

- [ ] Drag-and-drop lesson rescheduling
- [ ] Multi-language support (i18n)
- [ ] Student portal view
- [ ] Lecturer dashboard
- [ ] Room availability calendar
- [ ] Constraint suggestions (AI-powered)
- [ ] Comparison of multiple schedules
- [ ] Undo/Redo for manual changes

---

## Version History

| Version | Date | Major Features |
|---------|------|----------------|
| v1.0.0 | 2025-12-13 | Initial release, core scheduling |
| v1.1.0 | 2025-12-14 | Semester archiving |
| v1.2.0 | 2025-12-16 | Online classes, bulk import, frontend |
