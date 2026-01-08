import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class ApiService {
    private baseUrl = 'http://localhost:8080/api/v1';

    constructor(private http: HttpClient) { }

    // Stats
    getStats(): Observable<Stats> {
        return this.http.get<Stats>(`${this.baseUrl}/stats`);
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

    // Timetable
    getTimetable(params?: { student_group_id?: number; lecturer_id?: number; room_id?: number }): Observable<TimetableEntry[]> {
        return this.http.get<TimetableEntry[]>(`${this.baseUrl}/timetable`, { params: params as any });
    }

    // Lessons
    updateLesson(id: number, data: { assignedTimeslotId?: number; assignedRoomId?: number; pinned?: boolean }): Observable<TimetableEntry> {
        return this.http.patch<TimetableEntry>(`${this.baseUrl}/lessons/${id}`, data);
    }

    // Solver
    startSolver(mode: 'FULL_REPLAN' | 'STABILITY'): Observable<SolverStatus> {
        return this.http.post<SolverStatus>(`${this.baseUrl}/solver/solve`, { mode });
    }

    getSolverStatus(): Observable<SolverStatus> {
        return this.http.get<SolverStatus>(`${this.baseUrl}/solver/status`);
    }

    terminateSolver(): Observable<SolverStatus> {
        return this.http.post<SolverStatus>(`${this.baseUrl}/solver/terminate`, {});
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

    // Semesters
    getSemesterArchives(): Observable<SemesterArchive[]> {
        return this.http.get<SemesterArchive[]>(`${this.baseUrl}/semesters/archives`);
    }

    archiveSemester(data: { code: string; name?: string; academicYear?: string; semesterNumber?: number }): Observable<any> {
        return this.http.post(`${this.baseUrl}/semesters/archive`, data);
    }

    deleteSemesterArchive(code: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/semesters/${code}`);
    }
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
    allowedZones: string[];
    online: boolean;
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

export interface SolverStatus {
    jobId: string;
    state: string;
    score: string;
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
