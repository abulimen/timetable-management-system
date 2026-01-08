# Audit Logging Feature - Requirements Engineering Document

**Version:** 1.0  
**Date:** 2025-12-26  
**Status:** Draft - Pending Approval

---

## 1. Executive Summary 

This document defines the requirements for implementing a **complete and robust audit logging system** for the Babcock University Timetable Management System (BUTMS). The audit log will track all significant actions performed within the system, providing accountability, traceability, and compliance capabilities.

---

## 2. Business Requirements

### 2.1 Problem Statement
Currently, BUTMS lacks visibility into:
- Who made changes to the system
- What changes were made
- When changes occurred
- What the data looked like before/after changes

### 2.2 Business Goals
1. **Accountability** - Track which user performed each action
2. **Traceability** - Maintain complete history of all data changes
3. **Compliance** - Meet audit requirements for academic institutions
4. **Debugging** - Help diagnose issues by reviewing action history
5. **Recovery** - Understand what happened for potential data recovery

### 2.3 Success Metrics
- 100% of CREATE, UPDATE, DELETE operations logged
- Log retention of minimum 2 years
- Query response time < 500ms for recent logs
- Zero performance degradation > 5% on normal operations

---

## 3. Functional Requirements

### 3.1 Entities to Audit

| Entity | CREATE | UPDATE | DELETE | Priority |
|--------|--------|--------|--------|----------|
| Course | ✓ | ✓ | ✓ | High |
| Lesson | ✓ | ✓ | ✓ | High |
| Lecturer | ✓ | ✓ | ✓ | High |
| StudentGroup | ✓ | ✓ | ✓ | High |
| Room | ✓ | ✓ | ✓ | Medium |
| Zone | ✓ | ✓ | ✓ | Medium |
| Feature | ✓ | ✓ | ✓ | Medium |
| SpecialEvent | ✓ | ✓ | ✓ | Medium |
| Timeslot | ✓ | ✓ | ✓ | Low |
| ConstraintSetting | ✓ | ✓ | ✓ | High |

### 3.2 System Actions to Audit

| Action | Description | Priority |
|--------|-------------|----------|
| SOLVER_START | Timetable solver started | High |
| SOLVER_COMPLETE | Solver finished successfully | High |
| SOLVER_TERMINATE | Solver manually terminated | High |
| BULK_IMPORT | CSV bulk import executed | High |
| DATA_WIPE | System data wiped | Critical |
| SEMESTER_ARCHIVE | Semester archived | High |
| SEMESTER_RESTORE | Semester restored | High |
| EXPORT_PDF | Timetable exported to PDF | Low |
| EXPORT_EXCEL | Timetable exported to Excel | Low |
| SETTINGS_CHANGE | System settings modified | High |

### 3.3 Audit Log Data Model

Each audit log entry must capture:

```
AuditLog {
    id: Long (PK, auto-generated)
    timestamp: DateTime (when action occurred)
    
    // Actor Information
    actorType: Enum (USER, SYSTEM, SCHEDULER)
    actorId: String (user ID or "SYSTEM")
    actorName: String (display name)
    actorIpAddress: String (client IP)
    
    // Action Information
    action: Enum (CREATE, UPDATE, DELETE, SYSTEM_ACTION)
    entityType: String (e.g., "Course", "Lesson")
    entityId: String (ID of affected entity)
    entityName: String (human-readable identifier)
    
    // Change Details
    previousValue: JSON (before state, nullable)
    newValue: JSON (after state, nullable)
    changedFields: String[] (list of modified fields for UPDATE)
    
    // Context
    description: String (human-readable summary)
    requestId: String (correlation ID for tracking)
    sessionId: String (user session, if applicable)
    
    // Metadata
    success: Boolean (did action succeed?)
    errorMessage: String (if failed, why?)
}
```

### 3.4 Query & Filter Requirements

The system must support filtering logs by:

| Filter | Type | Required |
|--------|------|----------|
| Date range | From/To datetime | Yes |
| Entity type | Multi-select dropdown | Yes |
| Action type | Multi-select dropdown | Yes |
| Actor | Text search | Yes |
| Entity ID | Exact match | Yes |
| Success status | Boolean toggle | Yes |
| Free text search | Full-text in description | Optional |

### 3.5 Log Viewing Requirements

1. **List View**: Paginated table (25/50/100 per page)
2. **Detail View**: Expandable row or modal showing full entry
3. **Diff View**: For UPDATE actions, show before/after comparison
4. **Export**: Download filtered logs as CSV or JSON
5. **Real-time**: Optional auto-refresh for monitoring

---

## 4. Non-Functional Requirements

### 4.1 Performance
- Logging must be **asynchronous** - not block main operations
- Write latency: < 50ms per log entry
- Read latency: < 500ms for paginated queries
- Index optimization for common query patterns

### 4.2 Scalability
- Support 10,000+ log entries per day
- Efficient storage with potential archival strategy
- Consider log rotation after 2 years

### 4.3 Security
- Audit logs are **immutable** - no UPDATE or DELETE allowed
- Access restricted to admin users only
- Logs must not expose sensitive data (passwords, tokens)
- IP addresses captured for security tracing

### 4.4 Reliability
- Logging failures must NOT break main operations
- Graceful degradation if log storage unavailable
- Log entry includes success/failure of original action

### 4.5 Data Integrity
- Transactions for log writes
- No orphaned or partial log entries
- JSON serialization with proper escaping

---

## 5. Use Cases

### UC-01: Administrator Reviews Recent Changes
**Actor:** Admin User  
**Flow:**
1. Admin navigates to Audit Logs page
2. Filters by date range (last 7 days)
3. Scans list for suspicious activity
4. Clicks on entry to see full details
5. Views diff for UPDATE actions

### UC-02: Investigate Data Modification
**Actor:** Admin/Coordinator  
**Flow:**
1. User notices incorrect course data
2. Goes to Audit Logs, filters by entityType="Course" and entityId=X
3. Sees history of all changes to that course
4. Identifies who made the incorrect change and when
5. Uses previousValue to understand original data

### UC-03: Compliance Audit
**Actor:** External Auditor  
**Flow:**
1. Auditor requests logs for specific semester
2. Admin exports logs as CSV with date range filter
3. Provides audit trail for all scheduling decisions

### UC-04: Debug Solver Issues
**Actor:** System Administrator  
**Flow:**
1. Solver produces unexpected results
2. Admin filters for SOLVER_* actions
3. Reviews solver start/complete/terminate events
4. Correlates with any manual changes during that period

---

## 6. UI/UX Requirements

### 6.1 Audit Logs Page Location
- Under **SYSTEM** menu section (alongside Settings)
- Icon: 📋 or clipboard icon
- Access: Admin only

### 6.2 Main Table Columns
| Column | Width | Sortable |
|--------|-------|----------|
| Timestamp | 150px | Yes (default DESC) |
| Actor | 120px | Yes |
| Action | 80px | Yes |
| Entity | 100px | Yes |
| Entity Name | 200px | No |
| Description | Flex | No |
| Status | 60px | Yes |
| Actions | 60px | No |

### 6.3 Action Badges (Color-coded)
- CREATE: 🟢 Green
- UPDATE: 🟡 Yellow/Orange
- DELETE: 🔴 Red
- SYSTEM_ACTION: 🔵 Blue

### 6.4 Responsive Design
- Desktop: Full table view
- Tablet: Collapsible columns
- Mobile: Card-based list view

---

## 7. Technical Architecture

### 7.1 Backend Components

```
┌─────────────────────────────────────────────────────┐
│                   Controller Layer                   │
│   AuditLogController (GET /api/audit-logs)          │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Service Layer                      │
│   AuditLogService                                    │
│   - logAction(action, entity, actor, details)       │
│   - queryLogs(filters, pageable)                    │
│   - exportLogs(filters, format)                     │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  Aspect Layer                        │
│   AuditLoggingAspect (@Around entity operations)    │
│   - Captures before/after state automatically       │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  Repository Layer                    │
│   AuditLogRepository (JPA)                          │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Database                           │
│   audit_log table (MySQL)                           │
└─────────────────────────────────────────────────────┘
```

### 7.2 Logging Mechanism Options

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| **AOP Aspect** | Automatic, DRY | Complex setup | ✅ Preferred |
| Manual calls | Simple, explicit | Repetitive | Fallback |
| JPA Listeners | Built-in | Limited context | Supplement |

### 7.3 Async Processing
- Use `@Async` for log writing
- Separate thread pool for audit operations
- Queue-based processing for high volume

---

## 8. Database Schema

```sql
CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    timestamp DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    
    -- Actor
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(100),
    actor_name VARCHAR(255),
    actor_ip_address VARCHAR(45),
    
    -- Action
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    entity_name VARCHAR(255),
    
    -- Change Details
    previous_value JSON,
    new_value JSON,
    changed_fields VARCHAR(1000),
    
    -- Context
    description VARCHAR(500),
    request_id VARCHAR(50),
    session_id VARCHAR(100),
    
    -- Metadata
    success BOOLEAN DEFAULT TRUE,
    error_message VARCHAR(500),
    
    -- Indexes
    INDEX idx_timestamp (timestamp),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_actor (actor_id),
    INDEX idx_action (action),
    INDEX idx_composite (timestamp, entity_type, action)
);
```

---

## 9. API Endpoints

### 9.1 REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/audit-logs` | List logs with filters & pagination |
| GET | `/api/audit-logs/{id}` | Get single log entry details |
| GET | `/api/audit-logs/export` | Export filtered logs as CSV/JSON |
| GET | `/api/audit-logs/summary` | Get action counts by type/entity |

### 9.2 Query Parameters

```
GET /api/audit-logs?
    page=0&
    size=25&
    sort=timestamp,desc&
    startDate=2025-01-01T00:00:00&
    endDate=2025-12-31T23:59:59&
    entityTypes=Course,Lesson&
    actions=CREATE,UPDATE&
    actorId=user123&
    success=true
```

---

## 10. Security Considerations

1. **Access Control**: Only users with ADMIN role can view audit logs
2. **Data Sanitization**: Filter sensitive fields before logging (passwords, tokens)
3. **IP Logging**: Capture client IP for security investigations
4. **Immutability**: No API endpoints to modify or delete logs
5. **Encryption**: Consider encrypting previousValue/newValue if containing PII

---

## 11. Acceptance Criteria

### 11.1 Functional Tests
- [ ] All CRUD operations on entities create audit logs
- [ ] Solver actions (start, complete, terminate) are logged
- [ ] Bulk import creates consolidated log entry
- [ ] Data wipe creates critical log before wiping
- [ ] Filters work correctly individually and combined
- [ ] Pagination works for large result sets
- [ ] Export produces valid CSV/JSON

### 11.2 Non-Functional Tests
- [ ] Logging adds < 50ms to operation time
- [ ] Page loads in < 500ms with 10,000+ entries
- [ ] No data loss on concurrent logging
- [ ] System continues if logging fails

### 11.3 UI Tests
- [ ] Table displays correctly on desktop/mobile
- [ ] Action badges show correct colors
- [ ] Detail modal shows full information
- [ ] Diff view correctly highlights changes

---

## 12. Implementation Phases

### Phase 1: Core Infrastructure (Priority: Critical)
- Database migration for audit_log table
- AuditLog entity and repository
- AuditLogService with basic logging

### Phase 2: Automatic Logging (Priority: High)
- AOP aspect for entity operations
- System action logging (solver, import, etc.)
- Request context capture (IP, session)

### Phase 3: Query & API (Priority: High)
- REST endpoints with filtering
- Pagination and sorting
- Export functionality

### Phase 4: Frontend UI (Priority: Medium)
- Audit logs page with table
- Filter components
- Detail view / diff viewer

---

## 13. Open Questions

1. **User Authentication**: Currently no auth system. Should actor be "ANONYMOUS" or should we wait for auth implementation?
2. **Log Retention**: Archive logs older than 2 years or keep indefinitely?
3. **Change Details**: Log full entity JSON or only changed fields?
4. **Sensitive Data**: Any fields that should NEVER be logged?

---

## 14. Appendix

### A. Sample Log Entries

**CREATE Example:**
```json
{
  "id": 1,
  "timestamp": "2025-12-26T10:30:00.123",
  "actorType": "USER",
  "actorId": "admin",
  "actorName": "System Administrator",
  "action": "CREATE",
  "entityType": "Course",
  "entityId": "42",
  "entityName": "COSC201 - Data Structures",
  "previousValue": null,
  "newValue": {"id": 42, "code": "COSC201", "name": "Data Structures", ...},
  "description": "Created course COSC201 - Data Structures",
  "success": true
}
```

**UPDATE Example:**
```json
{
  "id": 2,
  "timestamp": "2025-12-26T11:45:00.456",
  "actorType": "USER",
  "actorId": "coordinator",
  "action": "UPDATE",
  "entityType": "Lesson",
  "entityId": "105",
  "entityName": "COSC201 - Monday 09:00",
  "previousValue": {"roomId": 5, "timeslotId": 10},
  "newValue": {"roomId": 8, "timeslotId": 15},
  "changedFields": ["roomId", "timeslotId"],
  "description": "Moved lesson from Room A to Room B, 09:00 to 11:00",
  "success": true
}
```

**SYSTEM_ACTION Example:**
```json
{
  "id": 3,
  "timestamp": "2025-12-26T14:00:00.789",
  "actorType": "SYSTEM",
  "actorId": "SCHEDULER",
  "action": "SYSTEM_ACTION",
  "entityType": null,
  "description": "Solver completed with score: 0hard/-15soft, 45 lessons scheduled",
  "success": true
}
```

---

**Document Status:** Ready for Review  
**Next Step:** Await user approval before proceeding to implementation plan
