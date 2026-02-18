import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { toApiUrl } from '../config/api-base-url';

@Injectable({
    providedIn: 'root'
})
export class ApiService {
    private baseUrl = toApiUrl('/api/v1');

    constructor(private http: HttpClient) { }

    // Stats
    getStats(): Observable<Stats> {
        return this.http.get<Stats>(`${this.baseUrl}/stats`);
    }

    // Insights
    getZoneInsights(): Observable<ZoneInsightsSummary> {
        return this.http.get<ZoneInsightsSummary>(`${this.baseUrl}/insights/zones/summary`);
    }

    getFeatureInsights(): Observable<FeatureInsightsSummary> {
        return this.http.get<FeatureInsightsSummary>(`${this.baseUrl}/insights/features/summary`);
    }

    getLecturerInsights(): Observable<LecturerInsightsSummary> {
        return this.http.get<LecturerInsightsSummary>(`${this.baseUrl}/insights/lecturers/summary`);
    }

    getCourseFeasibilityDiagnostics(): Observable<CourseFeasibilityDiagnostics> {
        return this.http.get<CourseFeasibilityDiagnostics>(`${this.baseUrl}/insights/diagnostics/course-feasibility`);
    }

    getFeatureScarcityDiagnostics(): Observable<FeatureScarcityDiagnostics> {
        return this.http.get<FeatureScarcityDiagnostics>(`${this.baseUrl}/insights/diagnostics/feature-scarcity`);
    }

    getLecturerLoadDiagnostics(): Observable<LecturerLoadDiagnostics> {
        return this.http.get<LecturerLoadDiagnostics>(`${this.baseUrl}/insights/diagnostics/lecturer-load`);
    }

    // Zones
    getZones(): Observable<Zone[]> {
        return this.http.get<Zone[]>(`${this.baseUrl}/zones`);
    }

    createZone(zone: Partial<Zone>): Observable<Zone> {
        return this.http.post<Zone>(`${this.baseUrl}/zones`, zone);
    }

    updateZone(id: number, zone: Partial<Zone>): Observable<Zone> {
        return this.http.put<Zone>(`${this.baseUrl}/zones/${id}`, zone);
    }

    deleteZone(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/zones/${id}`);
    }

    // Rooms
    getRooms(): Observable<Room[]> {
        return this.http.get<Room[]>(`${this.baseUrl}/rooms`);
    }

    createRoom(room: Partial<Room>): Observable<Room> {
        return this.http.post<Room>(`${this.baseUrl}/rooms`, room);
    }

    updateRoom(id: number, room: Partial<Room>): Observable<Room> {
        return this.http.put<Room>(`${this.baseUrl}/rooms/${id}`, room);
    }

    deleteRoom(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/rooms/${id}`);
    }

    // Features
    getFeatures(): Observable<Feature[]> {
        return this.http.get<Feature[]>(`${this.baseUrl}/features`);
    }

    createFeature(feature: Partial<Feature>): Observable<Feature> {
        return this.http.post<Feature>(`${this.baseUrl}/features`, feature);
    }

    deleteFeature(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/features/${id}`);
    }

    // Lecturers
    getLecturers(): Observable<Lecturer[]> {
        return this.http.get<Lecturer[]>(`${this.baseUrl}/lecturers`);
    }

    createLecturer(lecturer: Partial<Lecturer>): Observable<Lecturer> {
        return this.http.post<Lecturer>(`${this.baseUrl}/lecturers`, lecturer);
    }

    updateLecturer(id: number, lecturer: Partial<Lecturer>): Observable<Lecturer> {
        return this.http.put<Lecturer>(`${this.baseUrl}/lecturers/${id}`, lecturer);
    }

    deleteLecturer(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/lecturers/${id}`);
    }

    // Student Groups
    getStudentGroups(): Observable<StudentGroup[]> {
        return this.http.get<StudentGroup[]>(`${this.baseUrl}/student-groups`);
    }

    createStudentGroup(group: Partial<StudentGroup>): Observable<StudentGroup> {
        return this.http.post<StudentGroup>(`${this.baseUrl}/student-groups`, group);
    }

    updateStudentGroup(id: number, group: Partial<StudentGroup>): Observable<StudentGroup> {
        return this.http.put<StudentGroup>(`${this.baseUrl}/student-groups/${id}`, group);
    }

    deleteStudentGroup(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/student-groups/${id}`);
    }

    // Courses
    getCourses(): Observable<Course[]> {
        return this.http.get<Course[]>(`${this.baseUrl}/courses`);
    }

    createCourse(course: Partial<Course>): Observable<Course> {
        return this.http.post<Course>(`${this.baseUrl}/courses`, course);
    }

    updateCourse(id: number, course: Partial<Course>): Observable<Course> {
        return this.http.put<Course>(`${this.baseUrl}/courses/${id}`, course);
    }

    deleteCourse(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/courses/${id}`);
    }

    cancelCourse(id: number): Observable<CourseCancelResponse> {
        return this.http.post<CourseCancelResponse>(`${this.baseUrl}/courses/${id}/cancel`, {});
    }

    reassignCourseLecturer(id: number, lecturerId: number): Observable<CourseReassignResponse> {
        return this.http.post<CourseReassignResponse>(`${this.baseUrl}/courses/${id}/reassign-lecturer`, { lecturerId });
    }

    // Timetable
    getTimetable(params?: { student_group_id?: number; lecturer_id?: number; room_id?: number }): Observable<TimetableEntry[]> {
        return this.http.get<TimetableEntry[]>(`${this.baseUrl}/timetable`, { params: params as any });
    }

    getActiveSpecialEvents(): Observable<SpecialEventEntry[]> {
        return this.http.get<SpecialEventEntry[]>(`${this.baseUrl}/special-events/active`);
    }

    // Lessons
    updateLesson(id: number, data: { assignedTimeslotId?: number; assignedRoomId?: number; pinned?: boolean }): Observable<TimetableEntry> {
        return this.http.patch<TimetableEntry>(`${this.baseUrl}/lessons/${id}`, data);
    }

    // Solver
    startSolver(modeOrRequest: SolveMode | SolveRequest): Observable<SolverStatus> {
        const payload: SolveRequest = typeof modeOrRequest === 'string'
            ? { mode: modeOrRequest }
            : modeOrRequest;
        return this.http.post<SolverStatus>(`${this.baseUrl}/solver/solve`, payload);
    }

    getSolverStatus(): Observable<SolverStatus> {
        return this.http.get<SolverStatus>(`${this.baseUrl}/solver/status`);
    }

    getSolverRuntimeDiagnostics(): Observable<SolverRuntimeDiagnostics> {
        return this.http.get<SolverRuntimeDiagnostics>(`${this.baseUrl}/solver/runtime`);
    }

    runSolverBenchmark(request: SolverBenchmarkRequest = {}): Observable<SolverBenchmarkResult> {
        return this.http.post<SolverBenchmarkResult>(`${this.baseUrl}/solver/benchmark`, request);
    }

    terminateSolver(): Observable<SolverStatus> {
        return this.http.post<SolverStatus>(`${this.baseUrl}/solver/terminate`, {});
    }

    clearCurrentTimetable(): Observable<{ status: string; message: string; lessonsCleared: number }> {
        return this.http.post<{ status: string; message: string; lessonsCleared: number }>(
            `${this.baseUrl}/solver/clear-timetable`,
            {
                confirmationToken: 'CLEAR_TIMETABLE',
                secondConfirmationToken: 'CONFIRM_CLEAR'
            }
        );
    }

    getTimetableChangeStatus(): Observable<TimetableChangeStatus> {
        return this.http.get<TimetableChangeStatus>(`${this.baseUrl}/solver/change-status`);
    }

    enableEditingMode(): Observable<TimetableChangeStatus> {
        return this.http.post<TimetableChangeStatus>(`${this.baseUrl}/solver/editing/enable`, {
            confirmationToken: 'ENABLE_EDITING_FULL_REPLAN_REQUIRED'
        });
    }

    disableEditingMode(): Observable<TimetableChangeStatus> {
        return this.http.post<TimetableChangeStatus>(`${this.baseUrl}/solver/editing/disable`, {});
    }

    getSolverAnalysis(): Observable<SolverAnalysis> {
        return this.http.get<SolverAnalysis>(`${this.baseUrl}/solver/analysis`);
    }

    getFeasibility(): Observable<FeasibilityCheck> {
        return this.http.get<FeasibilityCheck>(`${this.baseUrl}/solver/feasibility`);
    }

    // Settings
    getSettings(): Observable<Setting[]> {
        return this.http.get<Setting[]>(`${this.baseUrl}/settings`);
    }

    updateSetting(key: string, value: string): Observable<Setting> {
        return this.http.put<Setting>(`${this.baseUrl}/settings/${key}`, { value });
    }

    regenerateTimeslots(): Observable<{ status: string; count: number; message: string }> {
        return this.http.post<{ status: string; count: number; message: string }>(
            `${this.baseUrl}/settings/regenerate-timeslots`,
            {}
        );
    }

    getUnavailabilitySystemSettings(): Observable<{ systemEnabled: boolean; requestsOpen: boolean }> {
        return this.http.get<{ systemEnabled: boolean; requestsOpen: boolean }>(
            `${this.baseUrl}/availability-requests/settings`
        );
    }

    updateUnavailabilitySystemSettings(payload: { systemEnabled?: boolean; requestsOpen?: boolean }): Observable<{ systemEnabled: boolean; requestsOpen: boolean; message?: string }> {
        return this.http.post<{ systemEnabled: boolean; requestsOpen: boolean; message?: string }>(
            `${this.baseUrl}/availability-requests/settings`,
            payload
        );
    }

    wipeSystemData(): Observable<{ message: string; totalDeleted: number }> {
        return this.http.delete<{ message: string; totalDeleted: number }>(`${this.baseUrl}/bulk/system-wipe`, {
            body: { confirmationToken: 'DELETE' }
        });
    }

    // Semesters
    getSemesterArchives(): Observable<SemesterArchive[]> {
        return this.http.get<SemesterArchive[]>(`${this.baseUrl}/semesters/archives`);
    }

    archiveSemester(data: { code: string; name?: string; academicYear?: string; semesterNumber?: number }): Observable<any> {
        return this.http.post(`${this.baseUrl}/semesters/archive`, data);
    }

    getArchivedSemesterTimetable(code: string): Observable<TimetableEntry[]> {
        return this.http.get<TimetableEntry[]>(`${this.baseUrl}/semesters/${code}/timetable`);
    }

    getArchivedSemesterGroups(code: string): Observable<StudentGroup[]> {
        return this.http.get<StudentGroup[]>(`${this.baseUrl}/semesters/${code}/groups`);
    }

    getArchivedSemesterSpecialEvents(code: string): Observable<SpecialEventEntry[]> {
        return this.http.get<SpecialEventEntry[]>(`${this.baseUrl}/semesters/${code}/special-events`);
    }

    exportArchivedSemesterTimetable(code: string): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/semesters/${code}/export`, {
            responseType: 'blob'
        });
    }

    exportArchivedTimetable(format: 'excel' | 'pdf', code: string, payload: ExportRequest): Observable<Blob> {
        return this.http.post(`${this.baseUrl}/semesters/${code}/export/${format}`, payload, {
            responseType: 'blob'
        });
    }

    restoreSemesterArchive(code: string): Observable<{ restoredFrom: string; backupCode: string; restoredTableCount: number }> {
        return this.http.post<{ restoredFrom: string; backupCode: string; restoredTableCount: number }>(
            `${this.baseUrl}/semesters/${code}/restore`,
            {}
        );
    }

    deleteSemesterArchive(code: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/semesters/${code}`);
    }

    // Export
    getExportDepartments(): Observable<ExportDepartment[]> {
        return this.http.get<ExportDepartment[]>(`${this.baseUrl}/export/departments`);
    }

    exportTimetable(format: 'excel' | 'pdf', payload: ExportRequest): Observable<Blob> {
        return this.http.post(`${this.baseUrl}/export/${format}`, payload, {
            responseType: 'blob'
        });
    }

    // ==================== STAGING AREA ====================
    submitToStaging(entity: string, file: File, note?: string): Observable<any> {
        const formData = new FormData();
        formData.append('file', file);
        if (note) {
            formData.append('note', note);
        }
        return this.http.post(`${this.baseUrl}/bulk/staging/${entity}`, formData);
    }

    getPendingBatches(): Observable<ImportBatch[]> {
        return this.http.get<ImportBatch[]>(`${this.baseUrl}/bulk/staging/pending`);
    }

    approveBatch(id: number, resolutions?: Record<number, string>): Observable<any> {
        return this.http.post(`${this.baseUrl}/bulk/staging/${id}/approve`, { resolutions });
    }

    rejectBatch(id: number, reason?: string): Observable<any> {
        return this.http.post(`${this.baseUrl}/bulk/staging/${id}/reject`, { reason });
    }

    previewBatch(id: number): Observable<BulkImportResult> {
        return this.http.get<BulkImportResult>(`${this.baseUrl}/bulk/staging/${id}/preview`);
    }

    // ==================== DRAFTS ====================

    createDraft(entity: string, file: File): Observable<any> {
        const formData = new FormData();
        formData.append('file', file);
        return this.http.post(`${this.baseUrl}/bulk/staging/draft/${entity}`, formData);
    }

    getMyDrafts(): Observable<ImportBatch[]> {
        return this.http.get<ImportBatch[]>(`${this.baseUrl}/bulk/staging/drafts`);
    }

    getMySubmissions(): Observable<ImportBatch[]> {
        return this.http.get<ImportBatch[]>(`${this.baseUrl}/bulk/staging/my-submissions`);
    }

    revertToDraft(id: number): Observable<any> {
        return this.http.post(`${this.baseUrl}/bulk/staging/${id}/revert-to-draft`, {});
    }

    getDraft(id: number): Observable<{ id: number; entityType: string; originalFilename: string; content: string; createdAt: string }> {
        return this.http.get<any>(`${this.baseUrl}/bulk/staging/draft/${id}`);
    }

    updateDraft(id: number, content: string): Observable<any> {
        return this.http.put(`${this.baseUrl}/bulk/staging/draft/${id}`, { content });
    }

    submitDraft(id: number): Observable<any> {
        return this.http.post(`${this.baseUrl}/bulk/staging/draft/${id}/submit`, {});
    }

    deleteDraft(id: number): Observable<any> {
        return this.http.delete(`${this.baseUrl}/bulk/staging/draft/${id}`);
    }
}

export interface ImportBatch {
    id: number;
    entityType: string;
    originalFilename: string;
    status: 'PENDING' | 'APPROVED' | 'REJECTED';
    createdAt: string;
    approvalDate?: string;
    submissionNote?: string;
    rejectionReason?: string;
    createdBy?: {
        id: number;
        firstName: string;
        lastName: string;
        email: string;
    };
    approvedBy?: {
        id: number;
        firstName: string;
        lastName: string;
    };
}

export interface BulkImportResult {
    createdCount: number;
    updatedCount: number;
    skippedCount: number;
    errorCount: number;
    validRows: ImportRowDetail[];
    rowErrors: ImportRowError[];
    globalErrors: string[];
    conflicts?: ImportConflict[];
}

export interface ImportRowDetail {
    rowNumber: number;
    data: Record<string, string>;
    status: string;
    message: string;
}

export interface ImportRowError {
    rowNumber: number;
    message: string;
    rawData: Record<string, string>;
}

export interface ImportConflict {
    rowNumber: number;
    key: string;
    keyType: string;
    existingId: number;
    existingData: Record<string, any>;
    newData: Record<string, any>;
    conflictingFields: string[];
    resolution?: 'KEEP_EXISTING' | 'UPDATE' | 'SKIP' | 'CREATE_NEW';
}

// Models
export interface Stats {
    courseCount: number;
    lessonCount: number;
    roomCount: number;
    lecturerCount: number;
    studentGroupCount: number;
    zoneCount: number;
    featureCount: number;
    timeslotCount: number;
    scheduledLessonCount: number;
    unscheduledLessonCount: number;
    pinnedLessonCount: number;
}

export interface ZoneInsightsSummaryItem {
    id: number;
    name: string;
    roomCount: number;
    capacity: number;
}

export interface ZoneInsightsSummary {
    totalZones: number;
    usedZones: number;
    unusedZones: number;
    totalRooms: number;
    totalCapacity: number;
    zones: ZoneInsightsSummaryItem[];
}

export interface FeatureInsightsSummaryItem {
    id: number;
    name: string;
    supplyCount: number;
    demandCount: number;
    scarcityRatio: number | null;
    unboundedScarcity: boolean;
}

export interface FeatureInsightsSummary {
    totalFeatures: number;
    orphanedFeatures: number;
    features: FeatureInsightsSummaryItem[];
}

export interface LecturerInsightsSummary {
    totalLecturers: number;
    noEmailCount: number;
    unassignedCount: number;
    overloadedCount: number;
    overloadThreshold: number;
    densityStartTime?: string;
    densityEndTime?: string;
    densitySlotHours?: number;
    densitySlots?: string[];
    unavailabilityDensity: Record<string, number>;
}

export interface DiagnosticsIssue {
    type: string;
    severity: 'BLOCKING' | 'WARNING';
    description: string;
    recommendation: string;
}

export interface CourseFeasibilityDiagnostics {
    feasible: boolean;
    lessonCount: number;
    timeslotCount: number;
    roomCount: number;
    availableRoomSlots: number;
    blockingCount: number;
    warningCount: number;
    blockingIssues: DiagnosticsIssue[];
    warningIssues: DiagnosticsIssue[];
}

export interface FeatureScarcityDiagnosticsItem {
    id: number;
    name: string;
    supplyCount: number;
    demandCount: number;
    scarcityRatio: number | null;
    unboundedScarcity: boolean;
    risk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}

export interface FeatureScarcityDiagnostics {
    totalFeatures: number;
    criticalCount: number;
    highCount: number;
    items: FeatureScarcityDiagnosticsItem[];
}

export interface LecturerLoadDiagnosticsItem {
    id: number;
    name: string;
    assignedHours: number;
    availableHours: number;
    unavailableSlots: number;
    loadRatio: number;
    risk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}

export interface LecturerLoadDiagnostics {
    totalTimeslots: number;
    criticalCount: number;
    highCount: number;
    items: LecturerLoadDiagnosticsItem[];
}

export interface Zone {
    id: number;
    name: string;
}

export interface Feature {
    id: number;
    name: string;
}

export interface Room {
    id: number;
    name: string;
    capacity: number;
    zoneName: string;
    zoneId: number;
    features: string[];
    featureIds: number[];
}

export interface Lecturer {
    id: number;
    name: string;
    email: string;
    unavailabilities: Unavailability[];
}

export interface Unavailability {
    id: number;
    dayOfWeek: string;
    startTime: string;
    endTime: string;
}

export interface StudentGroup {
    id: number;
    name: string;
    baseName?: string;
    level?: number;
    groupNotation?: string | null;
    size: number;
    parentGroupId: number | null;
    parentGroupName: string | null;
    childCount: number;
}

export interface Course {
    id: number;
    code: string;
    name: string;
    totalWeeklyHours: number;
    lecturerId: number | null;
    lecturerName: string | null;
    studentGroupId: number | null;  // Legacy single group
    studentGroupName: string | null;  // Legacy single group name
    studentGroupIds: number[];  // Multi-group support
    studentGroupNames: string[];  // Multi-group names
    requiredFeatures: string[];
    requiredFeatureIds?: number[];
    allowedZones: string[];
    allowedZoneIds?: number[];
    online: boolean;
    changeNotice?: string;
}

export interface TimetableEntry {
    lessonId: number;
    courseCode: string;
    courseName: string;
    partNumber: number;
    durationHours: number;
    dayOfWeek: string;
    startTime: string;
    endTime: string;
    roomId: number;
    roomName: string;
    roomCapacity: number;
    lecturerId: number;
    lecturerName: string;
    studentGroupId: number;
    studentGroupName: string;
    studentGroupSize: number;
    combined: boolean;
    combinedGroupNames: string[];
    totalStudentCount: number;
    pinned: boolean;
    scheduled: boolean;
    online: boolean;
}

export interface SpecialEventEntry {
    id: number;
    name: string;
    description: string | null;
    dayOfWeek: string;
    startTime: string;
    endTime: string;
    durationHours: number;
    roomId: number | null;
    roomName: string | null;
    lecturerId: number | null;
    lecturerName: string | null;
    online: boolean;
    active: boolean;
    studentGroupIds: number[];
    studentGroupNames: string[];
}

export interface SolverStatus {
    jobId: string;
    state: string;
    score: string;
    profile?: string | null;
    durationMs?: number | null;
    runStartedAt?: string | null;
    lastImprovementAt?: string | null;
    timeToFirstBestMs?: number | null;
    timeToFirstFeasibleMs?: number | null;
    improvementCount?: number | null;
    persistenceCount?: number | null;
    avgPersistenceMs?: number | null;
    lessonsCount?: number | null;
    timeslotsCount?: number | null;
    roomsCount?: number | null;
    moveThreadCount?: string | null;
    environmentMode?: string | null;
    parallelSolverCount?: string | null;
    availableProcessors?: number | null;
    adaptiveMaxRuntimeMs?: number | null;
    adaptiveUnimprovedMs?: number | null;
    adaptiveAcceptedCountLimit?: number | null;
    adaptiveDatasetBand?: 'SMALL' | 'MEDIUM' | 'LARGE' | string | null;
    adaptiveTerminationReason?: string | null;
    bestHardScore?: number | null;
    bestSoftScore?: number | null;
    feasible?: boolean | null;
    impactedLessonsCount?: number | null;
    lockedLessonsCount?: number | null;
    changedLockedLessonsCount?: number | null;
    pendingChanges?: boolean;
    pendingChangeReason?: string | null;
    pendingChangeSince?: string | null;
}

export type SolveMode = 'FULL_REPLAN' | 'STABILITY';

export interface SolveRequest {
    mode: SolveMode;
    profile?: 'BALANCED' | 'QUALITY';
    skipFeasibility?: boolean;
}

export interface SolverRuntimeDiagnostics {
    availableProcessors: number;
    moveThreadCount: string;
    environmentMode: string;
    parallelSolverCount: string;
    reproducible: boolean;
}

export interface SolverBenchmarkRequest {
    warmupRuns?: number;
    measuredRuns?: number;
    pollIntervalMs?: number;
    perRunTimeoutSeconds?: number;
    modes?: Array<'FULL_REPLAN' | 'STABILITY'>;
    profiles?: Array<'BALANCED' | 'QUALITY'>;
    skipFeasibility?: boolean;
}

export interface SolverBenchmarkSample {
    state: string;
    score: string;
    durationMs: number | null;
}

export interface SolverBenchmarkScenario {
    mode: string;
    profile: string;
    warmupSamples: SolverBenchmarkSample[];
    measuredSamples: SolverBenchmarkSample[];
    minDurationMs: number | null;
    p50DurationMs: number | null;
    p95DurationMs: number | null;
    maxDurationMs: number | null;
    avgDurationMs: number | null;
}

export interface SolverBenchmarkResult {
    startedAt: string;
    finishedAt: string;
    warmupRuns: number;
    measuredRuns: number;
    pollIntervalMs: number;
    perRunTimeoutSeconds: number;
    scenarios: SolverBenchmarkScenario[];
}

export interface TimetableChangeStatus {
    pendingChanges: boolean;
    reason: string | null;
    changedAt: string | null;
    editingEnabled: boolean;
}

export interface CourseCancelResponse {
    status: string;
    courseId: number;
    deletedLessons: number;
    changeNotice: string;
}

export interface CourseReassignResponse {
    status: string;
    course: Course;
    changeNotice: string;
}

export interface SolverAnalysis {
    score: string;
    feasible: boolean;
    hardViolationCount: number;
    softPenalty: number;
    hardViolations: ConstraintViolation[];
    softViolations: ConstraintViolation[];
}

export interface ConstraintViolation {
    constraintName: string;
    matchCount: number;
    scoreImpact: string;
    weight: number;
    details: { entity: string; description: string; recommendation: string }[];
}

export interface FeasibilityCheck {
    feasible: boolean;
    blockingCount: number;
    warningCount: number;
    lessonCount: number;
    timeslotCount: number;
    roomCount: number;
    availableRoomSlots: number;
    issues: { type: string; severity: string; description: string; recommendation: string }[];
}

export interface Setting {
    key: string;
    value: string;
    dataType: 'STRING' | 'INTEGER' | 'TIME' | 'BOOLEAN' | string;
    category: string;
    description: string;
}

export interface SemesterArchive {
    id: number;
    code: string;
    name: string;
    academicYear: string;
    semesterNumber: number;
    archivedAt: string;
    courseCount: number;
    lessonCount: number;
    studentGroupCount: number;
    lecturerCount: number;
}

export interface ExportDepartment {
    id: number;
    name: string;
    size: number;
    childCount: number;
    isParent: boolean;
}

export interface ExportRequest {
    groupIds: number[];
    title: string;
}
