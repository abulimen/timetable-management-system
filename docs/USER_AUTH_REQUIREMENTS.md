# User Authentication & Authorization - Requirements Engineering Document

**Version:** 1.0  
**Date:** 2025-12-26  
**Status:** Draft - Pending Approval

---

## 1. Executive Summary

This document defines the requirements for implementing a **complete and robust user authentication and authorization system** for the University Timetable Management System (BUTMS). The system will provide secure access control, role-based permissions, and session management to protect sensitive scheduling data.

---

## 2. Business Requirements

### 2.1 Problem Statement
Currently, BUTMS has:
- No user accounts or login functionality
- No access control - anyone can view/edit everything
- No way to track who made changes
- No protection for sensitive operations (data wipe, solver, etc.)

### 2.2 Business Goals
1. **Security** - Protect system from unauthorized access
2. **Accountability** - Track which user performs each action
3. **Role Separation** - Different access levels for different users
4. **Compliance** - Meet institutional security requirements
5. **Usability** - Seamless login experience without friction

### 2.3 Success Metrics
- 100% of protected endpoints require authentication
- Token-based stateless authentication
- Session timeout after configurable inactivity period
- Password complexity enforcement
- Role-based access working for all features

---

## 3. User Roles & Permissions

### 3.1 Role Hierarchy

```
SUPER_ADMIN (Full System Control)
    └── ADMIN (System Management)
         └── COORDINATOR (Timetable Management) 
              └── LECTURER (View + Limited Edit)
                   └── VIEWER (Read-Only)
```

### 3.2 Role Definitions

| Role | Description | Typical Users |
|------|-------------|---------------|
| **SUPER_ADMIN** | Full system control, can manage admins | IT Department Head |
| **ADMIN** | System configuration, user management | IT Staff |
| **COORDINATOR** | Timetable creation and management | Academic Coordinators |
| **LECTURER** | View schedules, manage own availability | Teaching Staff |
| **VIEWER** | Read-only access to timetables | Students, Guests |

### 3.3 Permission Matrix

| Feature | SUPER_ADMIN | ADMIN | COORDINATOR | LECTURER | VIEWER |
|---------|:-----------:|:-----:|:-----------:|:--------:|:------:|
| **User Management** |
| Create/Edit Users | ✓ | ✓ | ✗ | ✗ | ✗ |
| Delete Users | ✓ | ✗ | ✗ | ✗ | ✗ |
| Assign Roles | ✓ | ✓ (below own) | ✗ | ✗ | ✗ |
| View Users | ✓ | ✓ | ✗ | ✗ | ✗ |
| **Data Management** |
| Create/Edit Courses | ✓ | ✓ | ✓ | ✗ | ✗ |
| Create/Edit Lecturers | ✓ | ✓ | ✓ | ✗ | ✗ |
| Create/Edit Rooms | ✓ | ✓ | ✓ | ✗ | ✗ |
| Create/Edit StudentGroups | ✓ | ✓ | ✓ | ✗ | ✗ |
| Create/Edit Zones/Features | ✓ | ✓ | ✓ | ✗ | ✗ |
| Bulk Import | ✓ | ✓ | ✓ | ✗ | ✗ |
| **Timetable Operations** |
| View Timetable | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit Lessons | ✓ | ✓ | ✓ | ✗ | ✗ |
| Run Solver | ✓ | ✓ | ✓ | ✗ | ✗ |
| Export Timetable | ✓ | ✓ | ✓ | ✓ | ✓ |
| **System Operations** |
| View Settings | ✓ | ✓ | ✓ | ✗ | ✗ |
| Modify Settings | ✓ | ✓ | ✗ | ✗ | ✗ |
| Data Wipe | ✓ | ✗ | ✗ | ✗ | ✗ |
| Semester Archive | ✓ | ✓ | ✗ | ✗ | ✗ |
| View Audit Logs | ✓ | ✓ | ✗ | ✗ | ✗ |
| **Personal** |
| Edit Own Profile | ✓ | ✓ | ✓ | ✓ | ✓ |
| Change Own Password | ✓ | ✓ | ✓ | ✓ | ✓ |
| View Own Schedule | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Availability Controls** |
| Edit own availability (before deadline) | ✓ | ✓ | ✓ | ✓ | ✗ |
| Edit own availability (after deadline) | ✓ | ✓ | ✓ | **Request Only** | ✗ |
| Submit availability change request | ✓ | ✓ | ✓ | ✓ | ✗ |
| Approve availability change requests | ✓ | ✓ | ✓ | ✗ | ✗ |
| Edit ANY lecturer's availability | ✓ | ✓ | ✓ | ✗ | ✗ |
| Configure availability deadline | ✓ | ✓ | ✗ | ✗ | ✗ |
| Override availability restrictions | ✓ | ✓ | ✗ | ✗ | ✗ |

---

## 3.4 Lecturer Availability Safeguards

> **⚠️ IMPORTANT**: This section addresses the risk of lecturers abusing availability settings to avoid inconvenient scheduling.

### 3.4.1 Problem Statement

Without controls, lecturers could:
- Mark themselves unavailable for early mornings, late evenings, or Fridays
- Change availability after timetable is generated to disrupt schedules
- Continuously adjust availability to avoid certain classes
- Create scheduling chaos with no accountability

### 3.4.2 Safeguard 1: Availability Submission Window

```
┌─────────────────────────────────────────────────────────────────┐
│                    SEMESTER TIMELINE                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ← Availability Window →     ← Locked Period →                  │
│  [Lecturers can edit]        [Requests only]                    │
│                                                                 │
│  Aug 1 ──────────── Sept 1 ──────────── Dec 15                  │
│          OPEN              DEADLINE           END               │
│                               │                                 │
│                    Timetable Generated                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Configuration Settings:**
| Setting | Description | Default |
|---------|-------------|---------|
| `availability_window_start` | When lecturers can start setting availability | 60 days before semester |
| `availability_deadline` | Last day for direct edits | 7 days before timetable generation |
| `grace_period_after_publish` | Hours after timetable published for minor changes | 48 hours |

### 3.4.3 Safeguard 2: Approval Workflow for Late Changes

When a lecturer wants to change availability **AFTER the deadline**:

```
┌──────────────┐    ┌─────────────────┐    ┌────────────────┐
│   LECTURER   │───▶│  CHANGE REQUEST │───▶│  COORDINATOR   │
│  submits     │    │   (pending)     │    │  reviews       │
└──────────────┘    └─────────────────┘    └────────────────┘
                                                  │
                         ┌────────────────────────┼────────────────────────┐
                         ▼                        ▼                        ▼
                  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
                  │  APPROVED   │         │  REJECTED   │         │  RETURNED   │
                  │  + Reason   │         │  + Reason   │         │  for info   │
                  └─────────────┘         └─────────────┘         └─────────────┘
                         │                        │
                         ▼                        ▼
                  Availability              No change,
                  updated +                 lecturer
                  affected lessons          notified
                  flagged
```

**AvailabilityChangeRequest Entity:**
```
AvailabilityChangeRequest {
    id: Long
    lecturerId: Long
    requestedBy: Long (user ID)
    
    // What's being requested
    dayOfWeek: DayOfWeek
    startTime: LocalTime
    endTime: LocalTime
    newStatus: Enum (UNAVAILABLE, AVAILABLE, PREFERRED)
    reason: String (required, min 20 chars)
    
    // Workflow
    status: Enum (PENDING, APPROVED, REJECTED, RETURNED)
    reviewedBy: Long (user ID)
    reviewedAt: DateTime
    reviewNotes: String
    
    // Impact analysis
    affectedLessonsCount: Integer
    affectedLessonIds: String (comma-separated)
    
    createdAt: DateTime
}
```

### 3.4.4 Safeguard 3: Conflict Detection & Warning

When availability changes (direct or via request):

```
┌─────────────────────────────────────────────────────────────────┐
│ ⚠️ CONFLICT DETECTED                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Marking Monday 08:00-10:00 as UNAVAILABLE will affect:          │
│                                                                 │
│ • COSC201 - Data Structures (Monday 08:00, Room A101)           │
│   Students: COSC-2A (45 students)                               │
│                                                                 │
│ • COSC305 - Algorithms (Monday 09:00, Room B202)                │
│   Students: COSC-3A (38 students)                               │
│                                                                 │
│ These lessons will need to be rescheduled.                      │
│                                                                 │
│ [Cancel]  [Proceed Anyway - requires approval]                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**System Behavior:**
1. Before saving, check for scheduled lessons during the unavailable period
2. If conflicts found:
   - Before deadline: Show warning, require acknowledgment
   - After deadline: Block direct edit, force change request
3. Coordinator sees conflict count when reviewing requests

### 3.4.5 Safeguard 4: Availability Restriction Limits

Prevent lecturers from being "unavailable" too often:

| Restriction | Limit | Configurable |
|-------------|-------|--------------|
| **Max unavailable hours per week** | 20 hours (out of 55 possible: 11 hours × 5 days) | Yes |
| **Max unavailable days** | Cannot mark entire day unavailable | Yes |
| **Min available hours** | Must have at least 6 available hours per day | Yes |
| **Friday restriction** | Cannot mark all of Friday unavailable (special) | Yes |

**Validation on Save:**
```
ERROR: Availability exceeds limits
- You have marked 25 hours as unavailable (max: 20)
- Monday has only 4 available hours (min: 6 required)

Please adjust your availability or contact your coordinator.
```

### 3.4.6 Safeguard 5: Audit Trail for Availability

All availability changes logged:

```json
{
    "timestamp": "2025-12-31T10:00:00",
    "actor": "dr.smith@babcock.edu.ng",
    "action": "AVAILABILITY_CHANGE",
    "entityType": "LecturerUnavailability",
    "entityId": "45",
    "previousValue": {
        "dayOfWeek": "MONDAY",
        "startTime": "08:00",
        "endTime": "10:00",
        "status": "AVAILABLE"
    },
    "newValue": {
        "dayOfWeek": "MONDAY", 
        "startTime": "08:00",
        "endTime": "10:00",
        "status": "UNAVAILABLE"
    },
    "affectedLessons": ["COSC201", "COSC305"],
    "approvedBy": "coordinator@babcock.edu.ng",
    "approvalRequired": true
}
```

### 3.4.7 Settings for Availability Controls

New system settings:

| Setting Key | Description | Type | Default |
|-------------|-------------|------|---------|
| `availability_deadline_enabled` | Enforce deadline for self-edit | Boolean | true |
| `availability_deadline_date` | Last date for direct edits | Date | null |
| `availability_max_unavailable_hours` | Max hours per week | Integer | 20 |
| `availability_min_daily_hours` | Min hours per day | Integer | 6 |
| `availability_require_reason` | Require reason for unavailability | Boolean | true |
| `availability_approval_required_after_deadline` | Force approval after deadline | Boolean | true |
| `availability_grace_period_hours` | Hours after publish for changes | Integer | 48 |

---

## 4. Functional Requirements

### 4.1 User Account Management

#### 4.1.1 User Entity Fields

```
User {
    id: Long (PK, auto-generated)
    
    // Authentication
    email: String (unique, required) - login identifier
    password: String (hashed, required)
    
    // Profile
    firstName: String (required)
    lastName: String (required)
    phone: String (optional)
    department: String (optional)
    
    // Authorization
    role: Enum (SUPER_ADMIN, ADMIN, COORDINATOR, LECTURER, VIEWER)
    
    // Link to Lecturer (if applicable)
    lecturerId: Long (FK, nullable) - links to Lecturer entity
    
    // Status
    active: Boolean (default: true)
    emailVerified: Boolean (default: false)
    
    // Timestamps
    createdAt: DateTime
    updatedAt: DateTime
    lastLoginAt: DateTime (nullable)
    
    // Security
    failedLoginAttempts: Integer (default: 0)
    lockedUntil: DateTime (nullable)
    passwordChangedAt: DateTime
    mustChangePassword: Boolean (default: false)
}
```

#### 4.1.2 User Operations

| Operation | Description | Who Can Perform |
|-----------|-------------|-----------------|
| Create User | Register new user account | ADMIN+ |
| Update User | Edit user profile/role | ADMIN+ (own profile for all) |
| Delete User | Soft delete (deactivate) | SUPER_ADMIN |
| List Users | View all users | ADMIN+ |
| Reset Password | Admin-initiated reset | ADMIN+ |
| Lock/Unlock | Manually lock/unlock account | ADMIN+ |

### 4.2 Authentication Flows

#### 4.2.1 Login Flow

```
1. User submits email + password
2. System validates credentials
3. If failed:
   - Increment failedLoginAttempts
   - If >= 5 attempts, lock account for 30 minutes
   - Return error (don't reveal if email exists)
4. If success:
   - Reset failedLoginAttempts
   - Generate JWT access token (short-lived: 15 min)
   - Generate refresh token (long-lived: 7 days)
   - Update lastLoginAt
   - Return tokens + user info
```

#### 4.2.2 Token Refresh Flow

```
1. Client sends refresh token
2. System validates refresh token
3. If valid and not expired:
   - Generate new access token
   - Optionally rotate refresh token
   - Return new tokens
4. If invalid:
   - Return 401, client must re-login
```

#### 4.2.3 Logout Flow

```
1. Client sends logout request with refresh token
2. System invalidates refresh token (add to blacklist)
3. Client clears stored tokens
```

#### 4.2.4 Password Reset Flow (Future)

```
1. User requests reset via email
2. System generates time-limited reset token
3. Email sent with reset link
4. User clicks link, enters new password
5. System validates token, updates password
6. Invalidate all existing sessions
```

### 4.3 Authentication Requirements

| Requirement | Specification |
|-------------|---------------|
| **Password Minimum Length** | 8 characters |
| **Password Complexity** | At least 1 uppercase, 1 lowercase, 1 number |
| **Password Hashing** | BCrypt with cost factor 12 |
| **JWT Algorithm** | HS256 or RS256 |
| **Access Token Expiry** | 15 minutes |
| **Refresh Token Expiry** | 7 days |
| **Failed Login Lockout** | 5 attempts → 30 min lock |
| **Session Timeout** | Configurable (default: 30 min inactivity) |

### 4.4 API Security

#### 4.4.1 Protected vs Public Endpoints

**Public (No Auth Required):**
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password` (future)
- `GET /api/health`

**Protected (Auth Required):**
- All other endpoints

#### 4.4.2 Authorization Header

```
Authorization: Bearer <access_token>
```

#### 4.4.3 Error Responses

| Status | Meaning | Response |
|--------|---------|----------|
| 401 | Unauthorized | Token missing/invalid/expired |
| 403 | Forbidden | Valid token but insufficient permissions |

---

## 5. Technical Architecture

### 5.1 Backend Components

```
┌─────────────────────────────────────────────────────┐
│                  Security Filter Chain               │
│   JwtAuthenticationFilter → SecurityContextHolder   │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Auth Controller                    │
│   /auth/login, /auth/refresh, /auth/logout          │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Auth Service                       │
│   - authenticate(email, password)                   │
│   - generateTokens(user)                            │
│   - refreshToken(refreshToken)                      │
│   - validateToken(token)                            │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   JWT Service                        │
│   - createToken(claims, expiry)                     │
│   - parseToken(token)                               │
│   - validateSignature(token)                        │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  User Repository                     │
│   - findByEmail(email)                              │
│   - existsByEmail(email)                            │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Database                           │
│   user, refresh_token tables                        │
└─────────────────────────────────────────────────────┘
```

### 5.2 Spring Security Configuration

```java
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    // Stateless session (JWT)
    // CORS configuration
    // CSRF disabled for API
    // Public endpoints whitelist
    // All other requests authenticated
    // Custom JWT filter
    // Exception handling
}
```

### 5.3 Method-Level Security

```java
// Example usage in controllers
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long id) { ... }

@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public void createCourse(CourseDTO dto) { ... }

@PreAuthorize("hasRole('SUPER_ADMIN')")
public void wipeData() { ... }
```

---

## 6. Database Schema

### 6.1 Users Table

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Authentication
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Profile
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    department VARCHAR(100),
    
    -- Authorization
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    
    -- Link to Lecturer
    lecturer_id BIGINT,
    
    -- Status
    active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    last_login_at DATETIME(3),
    
    -- Security
    failed_login_attempts INT DEFAULT 0,
    locked_until DATETIME(3),
    password_changed_at DATETIME(3),
    must_change_password BOOLEAN DEFAULT FALSE,
    
    -- Indexes
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_active (active),
    
    -- Foreign Key
    FOREIGN KEY (lecturer_id) REFERENCES lecturers(id) ON DELETE SET NULL
);
```

### 6.2 Refresh Tokens Table

```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    
    INDEX idx_token (token),
    INDEX idx_user (user_id),
    INDEX idx_expires (expires_at),
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 6.3 Initial Admin User (Migration)

```sql
-- Create default super admin (password: Admin@123)
INSERT INTO users (email, password_hash, first_name, last_name, role, active, email_verified)
VALUES (
    'admin@babcock.edu.ng',
    '$2a$12$...', -- BCrypt hash of 'Admin@123'
    'System',
    'Administrator',
    'SUPER_ADMIN',
    TRUE,
    TRUE
);
```

---

## 7. API Endpoints

### 7.1 Authentication Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/login` | Authenticate user | Public |
| POST | `/api/auth/refresh` | Refresh access token | Public |
| POST | `/api/auth/logout` | Invalidate refresh token | Auth |
| GET | `/api/auth/me` | Get current user info | Auth |
| PUT | `/api/auth/password` | Change own password | Auth |

### 7.2 User Management Endpoints

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/api/users` | List all users | ADMIN+ |
| GET | `/api/users/{id}` | Get user by ID | ADMIN+ |
| POST | `/api/users` | Create new user | ADMIN+ |
| PUT | `/api/users/{id}` | Update user | ADMIN+ |
| DELETE | `/api/users/{id}` | Deactivate user | SUPER_ADMIN |
| POST | `/api/users/{id}/reset-password` | Reset password | ADMIN+ |
| POST | `/api/users/{id}/lock` | Lock account | ADMIN+ |
| POST | `/api/users/{id}/unlock` | Unlock account | ADMIN+ |

### 7.3 Request/Response Examples

**Login Request:**
```json
POST /api/auth/login
{
    "email": "coordinator@babcock.edu.ng",
    "password": "SecurePass123"
}
```

**Login Response:**
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2...",
    "expiresIn": 900,
    "tokenType": "Bearer",
    "user": {
        "id": 5,
        "email": "coordinator@babcock.edu.ng",
        "firstName": "John",
        "lastName": "Doe",
        "role": "COORDINATOR"
    }
}
```

---

## 8. Frontend Requirements

### 8.1 New Components

| Component | Description |
|-----------|-------------|
| LoginPage | Email/password form, remember me |
| AuthService | Token management, API calls |
| AuthGuard | Route protection |
| AuthInterceptor | Auto-attach tokens, handle 401 |
| UserManagementPage | CRUD for users (admin only) |
| ProfilePage | Edit own profile |
| ChangePasswordModal | Change own password |

### 8.2 Token Storage

- **Access Token**: Memory only (not localStorage)
- **Refresh Token**: HttpOnly cookie (preferred) or secure localStorage
- **Auto-refresh**: Refresh before access token expires

### 8.3 UI Changes

1. **Login Page**: New route `/login`
2. **Navigation**: Show/hide menu items based on role
3. **Protected Routes**: Redirect to login if not authenticated
4. **User Menu**: Top-right dropdown with profile, logout
5. **User Management**: New admin page `/users`

### 8.4 Role-Based UI

```typescript
// Show/hide based on role
@if (authService.hasRole('ADMIN')) {
    <a routerLink="/users">User Management</a>
}

// Disable buttons based on permissions
<button [disabled]="!authService.canEdit('courses')">
    Add Course
</button>
```

---

## 9. Security Considerations

### 9.1 Password Security
- BCrypt hashing (cost 12)
- Never log passwords
- Never return password hash in API
- Force password change for admin-created accounts

### 9.2 Token Security
- Short-lived access tokens (15 min)
- Refresh token rotation
- Token blacklisting on logout
- Secure token storage

### 9.3 API Security
- HTTPS required in production
- Rate limiting on login endpoint
- CORS configuration
- Input validation on all endpoints

### 9.4 Account Security
- Account lockout after failed attempts
- Email verification (future)
- Two-factor authentication (future)
- Audit logging of auth events

---

## 10. Acceptance Criteria

### 10.1 Authentication Tests
- [ ] User can login with valid credentials
- [ ] Invalid credentials return 401
- [ ] Account locks after 5 failed attempts
- [ ] Locked account auto-unlocks after 30 min
- [ ] Access token expires after 15 min
- [ ] Refresh token successfully generates new access token
- [ ] Logout invalidates refresh token
- [ ] Password change invalidates all sessions

### 10.2 Authorization Tests
- [ ] Protected endpoints return 401 without token
- [ ] Protected endpoints return 403 with insufficient role
- [ ] SUPER_ADMIN can access all features
- [ ] VIEWER cannot modify any data
- [ ] Users can only edit own profile
- [ ] Role-based menu items show/hide correctly

### 10.3 User Management Tests
- [ ] Admin can create new users
- [ ] Admin can update user roles (below own level)
- [ ] Only SUPER_ADMIN can delete users
- [ ] Password reset forces password change on next login
- [ ] Deactivated users cannot login

### 10.4 Availability Safeguard Tests
- [ ] Lecturer can edit own availability before deadline
- [ ] Lecturer CANNOT directly edit availability after deadline
- [ ] Lecturer can submit change request after deadline
- [ ] Coordinator receives notification of pending requests
- [ ] Coordinator can approve/reject requests
- [ ] Approved requests update lecturer availability
- [ ] Rejected requests leave availability unchanged
- [ ] Conflict detection shows affected lessons
- [ ] Restriction limits enforced (max unavailable hours)
- [ ] Validation prevents marking entire day unavailable
- [ ] All availability changes logged in audit trail
- [ ] Grace period allows changes within 48h of publish

---

## 11. Implementation Phases

### Phase 1: Core Authentication (Priority: Critical)
- Database migration (users, refresh_tokens)
- User entity and repository
- JWT service (create, validate, parse)
- AuthService (login, logout, refresh)
- Spring Security configuration
- JwtAuthenticationFilter

### Phase 2: User Management (Priority: High)  
- UserController (CRUD endpoints)
- Password hashing/validation
- Account lock/unlock
- Default admin user seed

### Phase 3: Frontend Auth (Priority: High)
- Login page component
- AuthService (Angular)
- AuthGuard and AuthInterceptor
- Token storage and auto-refresh
- Update navigation for roles

### Phase 4: Integration (Priority: Medium)
- Secure all existing endpoints
- Add role checks to controllers
- Role-based UI visibility
- User management admin page

### Phase 5: Availability Safeguards (Priority: Medium)
- AvailabilityChangeRequest entity and repository
- Database migration for change requests table
- Deadline configuration in settings
- Conflict detection service
- Restriction validation (max hours, min daily)
- Approval workflow API endpoints
- Frontend availability request UI
- Notification system for pending requests

---

## 12. Open Questions

1. **Email Verification**: Require email verification for new accounts?
2. **Self-Registration**: Allow users to register themselves or admin-only?
3. **Password Policy**: Any additional complexity requirements?
4. **Session Duration**: 7 days for refresh token acceptable?
5. **Lecturer Linking**: Auto-create user when creating lecturer?

---

## 13. Dependencies

- `spring-boot-starter-security`
- `jjwt` (io.jsonwebtoken) or `nimbus-jose-jwt`
- BCrypt (included in Spring Security)

---

## 14. Migration Path

Since the system currently has no auth:
1. Deploy auth system with default admin account
2. Admin creates user accounts for existing coordinators
3. Link Lecturer entities to User accounts
4. Enable auth enforcement (protected endpoints)
5. Communicate credentials to users

---

**Document Status:** Ready for Review  
**Next Step:** Await user approval before proceeding to implementation plan
