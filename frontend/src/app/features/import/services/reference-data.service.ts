import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, firstValueFrom } from 'rxjs';

export interface LecturerRef {
    id: number;
    name: string;
    email: string;
}

export interface StudentGroupRef {
    id: number;
    name: string;
    parentGroupName?: string;
    baseName?: string;        // Extracted from name (e.g., "CS" from "CS 100 LEVEL")
    isParentGroup?: boolean;  // True if this group has no parent (is a parent itself)
}

export interface ZoneRef {
    id: number;
    name: string;
}

export interface FeatureRef {
    id: number;
    name: string;
}

export interface UserIdentifierRef {
    email: string;
    phone?: string;
}



export interface ReferenceData {
    lecturers: LecturerRef[];
    studentGroups: StudentGroupRef[];
    zones: ZoneRef[];
    features: FeatureRef[];
    userIdentifiers: UserIdentifierRef[]; // New field
    loaded: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class ReferenceDataService {
    private http = inject(HttpClient);
    private baseUrl = 'http://localhost:8080/api/v1';
    private userUrl = 'http://localhost:8080/api/users'; // Admin API

    private dataSubject = new BehaviorSubject<ReferenceData>({
        lecturers: [],
        studentGroups: [],
        zones: [],
        features: [],
        userIdentifiers: [],
        loaded: false
    });

    public data$ = this.dataSubject.asObservable();

    /**
     * Load all reference data from the server.
     * Results are cached in the BehaviorSubject.
     */
    async loadAll(): Promise<ReferenceData> {
        try {
            // Note: getUserIdentifiers requires ADMIN role. If this fails (e.g. 403), we just ignore it for now.
            // But usually the import feature is ADMIN only.
            const [lecturers, studentGroups, zones, features] = await Promise.all([
                firstValueFrom(this.http.get<LecturerRef[]>(`${this.baseUrl}/lecturers`)),
                firstValueFrom(this.http.get<StudentGroupRef[]>(`${this.baseUrl}/student-groups`)),
                firstValueFrom(this.http.get<ZoneRef[]>(`${this.baseUrl}/zones`)),
                firstValueFrom(this.http.get<FeatureRef[]>(`${this.baseUrl}/features`))
            ]);

            // Attempt to load user identifiers (might fail if not admin, catch separately)
            let userIdentifiers: UserIdentifierRef[] = [];
            try {
                userIdentifiers = await firstValueFrom(this.http.get<UserIdentifierRef[]>(`${this.userUrl}/identifiers`));
            } catch (e) {
                console.warn('Failed to load user identifiers (probably not admin or endpoint missing)', e);
            }

            const data: ReferenceData = {
                lecturers: lecturers || [],
                studentGroups: (studentGroups || []).map(g => ({
                    ...g,
                    // Extract base name from full name (e.g., "CS 100 LEVEL" -> "CS", "CS 100 LEVEL (GRP A)" -> "CS")
                    baseName: this.extractBaseName(g.name),
                    // A group is a parent if it has no parentGroupName
                    isParentGroup: !g.parentGroupName || g.parentGroupName.trim() === ''
                })),
                zones: zones || [],
                features: features || [],
                userIdentifiers: userIdentifiers || [],
                loaded: true
            };

            this.dataSubject.next(data);
            return data;
        } catch (error) {
            console.error('Failed to load reference data:', error);
            throw error;
        }
    }

    /**
     * Get current cached data (synchronous).
     */
    getData(): ReferenceData {
        return this.dataSubject.value;
    }

    /**
     * Get list of lecturer emails for autocomplete.
     */
    getLecturerEmails(): string[] {
        return this.dataSubject.value.lecturers.map(l => l.email);
    }

    /**
     * Get list of student group names for autocomplete.
     */
    getStudentGroupNames(): string[] {
        return this.dataSubject.value.studentGroups.map(g => g.name);
    }

    /**
     * Get list of zone names for autocomplete.
     */
    getZoneNames(): string[] {
        return this.dataSubject.value.zones.map(z => z.name);
    }

    /**
     * Get list of feature names for autocomplete.
     */
    getFeatureNames(): string[] {
        return this.dataSubject.value.features.map(f => f.name);
    }

    /**
     * Force reload from server (cache invalidation).
     */
    async refresh(): Promise<ReferenceData> {
        return this.loadAll();
    }

    /**
     * Check if a lecturer email exists.
     */
    hasLecturerEmail(email: string): boolean {
        return this.dataSubject.value.lecturers.some(
            l => l.email.toLowerCase() === email.toLowerCase()
        );
    }

    /**
     * Check if a student group name exists.
     */
    hasStudentGroup(name: string): boolean {
        return this.dataSubject.value.studentGroups.some(
            g => g.name.toLowerCase() === name.toLowerCase()
        );
    }

    /**
     * Check if a zone name exists.
     */
    hasZone(name: string): boolean {
        return this.dataSubject.value.zones.some(
            z => z.name.toLowerCase() === name.toLowerCase()
        );
    }

    /**
     * Check if a feature name exists.
     */
    hasFeature(name: string): boolean {
        return this.dataSubject.value.features.some(
            f => f.name.toLowerCase() === name.toLowerCase()
        );
    }

    /**
     * Check if a user email exists (in DB).
     */
    hasUserEmail(email: string): boolean {
        if (!email) return false;
        return this.dataSubject.value.userIdentifiers.some(
            u => u.email.toLowerCase() === email.trim().toLowerCase()
        );
    }

    /**
     * Check if a user phone exists (in DB).
     */
    hasUserPhone(phone: string): boolean {
        if (!phone) return false;
        const normalizedPhone = phone.replace(/[^\d+]/g, '');
        return this.dataSubject.value.userIdentifiers.some(
            u => u.phone && u.phone.replace(/[^\d+]/g, '') === normalizedPhone
        );
    }

    /**
     * Extract base name from a full group name.
     * Examples:
     *  - "CS 100 LEVEL" -> "CS"
     *  - "CS 100 LEVEL (GRP A)" -> "CS"
     *  - "Computer Science 200 LEVEL" -> "Computer Science"
     */
    private extractBaseName(name: string): string {
        if (!name) return '';
        // Pattern: "BASE_NAME LEVEL_NUMBER LEVEL" optionally followed by "(GRP X)"
        // Remove (GRP X) suffix first if present
        let cleanName = name.replace(/\s*\(GRP\s+[^)]+\)\s*$/i, '').trim();
        // Match "XXX NUMBER LEVEL" pattern
        const match = cleanName.match(/^(.+?)\s+\d{3}\s+LEVEL$/i);
        if (match) {
            return match[1].trim();
        }
        // Fallback: return first word
        return cleanName.split(' ')[0] || cleanName;
    }

    /**
     * Get names of parent student groups, optionally filtered by base name.
     * @param baseName Optional base name to filter by (e.g., "CS" to get only "CS XXX LEVEL" groups)
     */
    getParentStudentGroups(baseName?: string): string[] {
        const groups = this.dataSubject.value.studentGroups
            .filter(g => g.isParentGroup);

        if (baseName && baseName.trim() !== '') {
            const normalizedBase = baseName.trim().toLowerCase();
            return groups
                .filter(g => g.baseName?.toLowerCase() === normalizedBase)
                .map(g => g.name);
        }

        return groups.map(g => g.name);
    }

    /**
     * Get full student group data (for advanced filtering).
     */
    getStudentGroupsData(): StudentGroupRef[] {
        return this.dataSubject.value.studentGroups;
    }
}
