import { ColDef, ValueFormatterParams } from 'ag-grid-community';
import { ReferenceDataService } from '../services/reference-data.service';
import {
    requiredValidator,
    emailValidator,
    phoneValidator,
    positiveIntValidator,
    nonNegativeIntValidator,
    booleanValidator,
    courseCodeValidator,
    roleValidator,
    createForeignKeyValidator,
    combineValidators,
    CellValidator
} from './cell-validators';

/**
 * Column definition with validation.
 */
export interface ValidatedColDef extends ColDef {
    validator?: CellValidator;
    autocompleteValues?: () => string[];
    referenceCheck?: boolean;
    supportsMultiple?: boolean; // Indicates if column supports pipe-separated values
    placeholder?: string;
    required?: boolean;
    strictAutocomplete?: boolean;
}

/**
 * Entity type for column definitions.
 */
export type EntityType = 'lecturers' | 'courses' | 'student-groups' | 'rooms' | 'zones' | 'features' | 'users';

/**
 * Get column definitions for a specific entity type.
 */
export function getColumnDefinitions(
    entityType: EntityType,
    refDataService: ReferenceDataService
): ValidatedColDef[] {
    switch (entityType) {
        case 'lecturers':
            return getLecturerColumns();
        case 'courses':
            return getCourseColumns(refDataService);
        case 'student-groups':
            return getStudentGroupColumns(refDataService);
        case 'rooms':
            return getRoomColumns(refDataService);
        case 'zones':
            return getZoneColumns();
        case 'features':
            return getFeatureColumns();
        case 'users':
            return getUserColumns(refDataService);
        default:
            return [];
    }
}

// ... (other functions remain same) ...

function getUserColumns(refDataService: ReferenceDataService): ValidatedColDef[] {
    const normalizeEmail = (value: any) => String(value || '').trim().toLowerCase();
    const normalizePhone = (value: any) => String(value || '').replace(/\D/g, '');

    const duplicateEmailValidator: CellValidator = (value: any, _rowData?: any, allRows?: any[]) => {
        const email = normalizeEmail(value);
        if (!email) {
            return { valid: true, status: 'valid' };
        }

        // 1. Check for duplicates within the current spreadsheet/grid
        if (Array.isArray(allRows)) {
            const count = allRows.filter(row => normalizeEmail(row?.email) === email).length;
            if (count > 1) {
                return { valid: false, status: 'error', message: `Duplicate email '${email}' found in spreadsheet` };
            }
        }

        // 2. Check against database (existing users)
        if (refDataService.hasUserEmail(email)) {
            return { valid: false, status: 'error', message: `Email '${email}' already exists in database` };
        }

        return { valid: true, status: 'valid' };
    };

    const duplicatePhoneValidator: CellValidator = (value: any, _rowData?: any, allRows?: any[]) => {
        const phone = String(value || '').trim();
        if (!phone) {
            return { valid: true, status: 'valid' };
        }
        const normalized = normalizePhone(phone);
        if (!normalized) {
            return { valid: true, status: 'valid' };
        }

        // 1. Check for duplicates in current file
        if (Array.isArray(allRows)) {
            const count = allRows.filter(row => normalizePhone(row?.phone) === normalized).length;
            if (count > 1) {
                return { valid: false, status: 'error', message: `Duplicate phone '${phone}' found in spreadsheet` };
            }
        }

        // 2. Check against database
        if (refDataService.hasUserPhone(normalized)) {
            return { valid: false, status: 'error', message: `Phone '${phone}' already exists in database` };
        }

        return { valid: true, status: 'valid' };
    };

    return [
        {
            field: 'email',
            headerName: 'Email',
            editable: true,
            flex: 1,
            required: true,
            validator: combineValidators(requiredValidator, emailValidator, duplicateEmailValidator),
            placeholder: 'Required'
        },
        {
            field: 'first_name',
            headerName: 'First Name',
            editable: true,
            width: 150,
            required: true,
            validator: requiredValidator,
            placeholder: 'Required'
        },
        {
            field: 'last_name',
            headerName: 'Last Name',
            editable: true,
            width: 150,
            required: true,
            validator: requiredValidator,
            placeholder: 'Required'
        },
        {
            field: 'role',
            headerName: 'Role',
            editable: true,
            width: 120,
            validator: roleValidator,
            required: true,
            autocompleteValues: () => ['ADMIN', 'COORDINATOR', 'LECTURER', 'VIEWER'],
            strictAutocomplete: true,
            placeholder: 'ADMIN/COORDINATOR/LECTURER/VIEWER'
        },
        {
            field: 'department',
            headerName: 'Department (Optional)',
            editable: true,
            flex: 1,
            required: false,
            placeholder: 'Optional'
        },
        {
            field: 'phone',
            headerName: 'Phone (Optional)',
            editable: true,
            width: 150,
            required: false,
            validator: combineValidators(phoneValidator, duplicatePhoneValidator),
            placeholder: 'Optional'
        }
    ];
}

function getLecturerColumns(): ValidatedColDef[] {
    return [
        {
            field: 'name',
            headerName: 'Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        },
        {
            field: 'email',
            headerName: 'Email',
            editable: true,
            flex: 1,
            validator: combineValidators(requiredValidator, emailValidator)
        }
    ];
}

function getCourseColumns(refDataService: ReferenceDataService): ValidatedColDef[] {
    const validateCourseGroupOverlap = (value: any, rowData?: any, allRows?: any[]) => {
        const code = String(rowData?.code || '').trim().toUpperCase();
        if (!code) {
            return { valid: true, status: 'valid' as const };
        }

        const rawGroups = String(value || '')
            .split('|')
            .map(v => v.trim())
            .filter(v => v.length > 0);

        if (rawGroups.length === 0) {
            return { valid: true, status: 'valid' as const };
        }

        const normalizedGroups = rawGroups.map(group => group.toLowerCase());
        const uniqueGroups = new Set(normalizedGroups);
        if (uniqueGroups.size !== normalizedGroups.length) {
            return { valid: false, status: 'error' as const, message: 'Duplicate student groups in same row are not allowed' };
        }

        if (!Array.isArray(allRows) || allRows.length === 0) {
            return { valid: true, status: 'valid' as const };
        }

        const usageCount = new Map<string, number>();
        for (const candidate of allRows) {
            const candidateCode = String(candidate?.code || '').trim().toUpperCase();
            if (candidateCode !== code) {
                continue;
            }

            const candidateGroups = String(candidate?.student_group_names || '')
                .split('|')
                .map((v: string) => v.trim())
                .filter((v: string) => v.length > 0)
                .map((v: string) => v.toLowerCase());

            if (candidateGroups.length === 0) {
                continue;
            }
            for (const group of candidateGroups) {
                usageCount.set(group, (usageCount.get(group) || 0) + 1);
            }
        }

        const overlappingGroups = Array.from(uniqueGroups).filter(group => (usageCount.get(group) || 0) > 1);
        if (overlappingGroups.length > 0) {
            return {
                valid: false,
                status: 'error' as const,
                message: `Duplicate assignment for code ${code}: group(s) already used -> ${overlappingGroups
                    .map(group => group.toUpperCase())
                    .join(', ')}`
            };
        }

        return { valid: true, status: 'valid' as const };
    };

    return [
        {
            field: 'code',
            headerName: 'Code',
            editable: true,
            width: 120,
            validator: courseCodeValidator
        },
        {
            field: 'name',
            headerName: 'Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        },
        {
            field: 'weekly_hours',
            headerName: 'Weekly Hours',
            editable: true,
            width: 100,
            validator: positiveIntValidator
        },
        {
            field: 'lecturer_email',
            headerName: 'Lecturer Email',
            editable: true,
            flex: 1,
            validator: createForeignKeyValidator(refDataService, 'lecturer'),
            referenceCheck: true,
            autocompleteValues: () => refDataService.getLecturerEmails()
        },
        {
            field: 'student_group_names',
            headerName: 'Student Groups',
            editable: true,
            flex: 1,
            validator: (value: any, rowData?: any, allRows?: any[]) => {
                const refCheck = createForeignKeyValidator(refDataService, 'studentGroup', true)(value, rowData, allRows);
                if (!refCheck.valid) {
                    return refCheck;
                }
                return validateCourseGroupOverlap(value, rowData, allRows);
            },
            referenceCheck: true,
            autocompleteValues: () => refDataService.getStudentGroupNames(),
            tooltipValueGetter: () => 'Separate multiple groups with |',
            supportsMultiple: true
        },
        {
            field: 'is_online',
            headerName: 'Online?',
            editable: true,
            width: 90,
            validator: booleanValidator,
            valueFormatter: (params: ValueFormatterParams) => {
                const val = String(params.value || '').toLowerCase();
                if (['true', 'yes', '1'].includes(val)) return 'Yes';
                if (['false', 'no', '0'].includes(val)) return 'No';
                return params.value || '';
            }
        },
        {
            field: 'required_features',
            headerName: 'Required Features',
            editable: true,
            flex: 1,
            validator: createForeignKeyValidator(refDataService, 'feature', true),
            referenceCheck: true,
            autocompleteValues: () => refDataService.getFeatureNames(),
            tooltipValueGetter: () => 'Separate multiple features with |',
            supportsMultiple: true
        },
        {
            field: 'allowed_zones',
            headerName: 'Allowed Zones',
            editable: true,
            flex: 1,
            validator: createForeignKeyValidator(refDataService, 'zone', true),
            referenceCheck: true,
            autocompleteValues: () => refDataService.getZoneNames(),
            tooltipValueGetter: () => 'Separate multiple zones with |',
            supportsMultiple: true
        }
    ];
}

function getStudentGroupColumns(refDataService: ReferenceDataService): ValidatedColDef[] {
    return [
        {
            field: 'base_name',
            headerName: 'Base Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        },
        {
            field: 'is_parent',
            headerName: 'Is Parent?',
            editable: true,
            width: 100,
            placeholder: 'T or F',
            autocompleteValues: () => ['T', 'F'],
            validator: (value) => {
                const v = String(value || '').trim().toUpperCase();
                if (v === '') {
                    return { valid: false, status: 'error' as const, message: '⚠️ Required: Enter T (parent group) or F (child group)' };
                }
                if (v !== 'T' && v !== 'F') {
                    return { valid: false, status: 'error' as const, message: `❌ Invalid value "${value}". Only T (True = Parent) or F (False = Child) allowed.` };
                }
                return { valid: true, status: 'valid' as const };
            }
        },
        {
            field: 'level',
            headerName: 'Level',
            editable: true,
            width: 80,
            validator: combineValidators(requiredValidator, (value) => {
                const validLevels = [100, 200, 300, 400, 500, 600];
                const num = parseInt(value, 10);
                if (isNaN(num) || !validLevels.includes(num)) {
                    return { valid: false, status: 'error' as const, message: 'Level must be 100, 200, 300, 400, 500, or 600' };
                }
                return { valid: true, status: 'valid' as const };
            })
        },
        {
            field: 'group',
            headerName: 'Group',
            editable: true,
            width: 80,
            validator: (value, rowData) => {
                // If NOT a parent, group is required
                const isParent = String(rowData?.is_parent || '').trim().toUpperCase() === 'T';
                if (!isParent && (!value || String(value).trim() === '')) {
                    return { valid: false, status: 'error' as const, message: 'Required for child groups' };
                }
                if (isParent && value && String(value).trim() !== '') {
                    return { valid: false, status: 'error' as const, message: 'Parent groups cannot have group notation' };
                }
                return { valid: true, status: 'valid' as const };
            }
        },
        {
            field: 'size',
            headerName: 'Size',
            editable: (params: any) => {
                // Size is read-only for parents (auto-calculated)
                const isParent = String(params?.data?.is_parent || '').trim().toUpperCase() === 'T';
                return !isParent;
            },
            width: 100,
            valueFormatter: (params: any) => {
                const isParent = String(params?.data?.is_parent || '').trim().toUpperCase() === 'T';
                if (isParent) return '(auto)';
                return params.value || '';
            },
            validator: (value, rowData) => {
                const isParent = String(rowData?.is_parent || '').trim().toUpperCase() === 'T';
                if (isParent) return { valid: true, status: 'valid' as const }; // Auto-calculated
                // For children, size must be positive
                const size = parseInt(value || '0', 10);
                if (isNaN(size) || size <= 0) {
                    return { valid: false, status: 'error' as const, message: 'Size must be positive for child groups' };
                }
                return { valid: true, status: 'valid' as const };
            }
        },
        {
            field: 'parent_group_name',
            headerName: 'Parent Group',
            editable: (params: any) => {
                // Parent groups don't have a parent
                const isParent = String(params?.data?.is_parent || '').trim().toUpperCase() === 'T';
                return !isParent;
            },
            flex: 1,
            // Show only parent groups from DB (groups with no parent of their own)
            autocompleteValues: () => refDataService.getParentStudentGroups(),
            validator: (value, rowData, allRows) => {
                const isParent = String(rowData?.is_parent || '').trim().toUpperCase() === 'T';
                const currentBaseName = String(rowData?.base_name || '').trim();

                // Parent groups cannot have a parent
                if (isParent && value && String(value).trim() !== '') {
                    return { valid: false, status: 'error' as const, message: '❌ Parent groups (Is Parent? = T) cannot have a parent. Leave empty.' };
                }

                // Child groups MUST have a parent group
                if (!isParent && (!value || String(value).trim() === '')) {
                    // Suggest parent groups with matching base name
                    const matchingParents = refDataService.getParentStudentGroups(currentBaseName).slice(0, 3);
                    const hint = matchingParents.length > 0
                        ? ` Try: ${matchingParents.join(', ')}`
                        : currentBaseName ? ` (No parent found for base "${currentBaseName}")` : '';
                    return { valid: false, status: 'error' as const, message: `⚠️ Child groups must have a parent.${hint}` };
                }

                // If parent is specified, validate it
                if (value && String(value).trim() !== '') {
                    const parentName = String(value).trim();

                    // Get all valid parent groups from DB (those with no parent themselves)
                    const dbParentGroups = refDataService.getStudentGroupsData().filter(g => g.isParentGroup);
                    const dbParentNames = dbParentGroups.map(g => g.name);

                    // Get parent groups from current import (is_parent = T)
                    let spreadsheetParents: { name: string; baseName: string }[] = [];
                    if (allRows) {
                        spreadsheetParents = allRows
                            .filter(r => String(r.is_parent || '').trim().toUpperCase() === 'T')
                            .map(r => {
                                const baseName = String(r.base_name || '').trim();
                                const level = String(r.level || '').trim();
                                return { name: baseName + ' ' + level + ' LEVEL', baseName };
                            });
                    }

                    // Check if parent exists and is a TRUE parent
                    const dbMatch = dbParentGroups.find(g => g.name.toLowerCase() === parentName.toLowerCase());
                    const ssMatch = spreadsheetParents.find(p => p.name.toLowerCase() === parentName.toLowerCase());

                    if (!dbMatch && !ssMatch) {
                        // Parent not found at all
                        const allParentNames = [...new Set([...dbParentNames, ...spreadsheetParents.map(p => p.name)])];
                        const similar = allParentNames.filter(p =>
                            p.toLowerCase().startsWith(currentBaseName.toLowerCase())
                        ).slice(0, 3);

                        let errorMsg = `❌ "${parentName}" is not a valid parent group.`;
                        if (similar.length > 0) {
                            errorMsg += ` Try: ${similar.join(', ')}`;
                        }
                        return { valid: false, status: 'error' as const, message: errorMsg };
                    }

                    // RULE: Parent's base name must match child's base name
                    const parentBaseName = dbMatch?.baseName || ssMatch?.baseName || '';
                    if (currentBaseName && parentBaseName &&
                        parentBaseName.toLowerCase() !== currentBaseName.toLowerCase()) {
                        return {
                            valid: false,
                            status: 'error' as const,
                            message: `❌ Base name mismatch! Child base is "${currentBaseName}" but parent "${parentName}" has base "${parentBaseName}".`
                        };
                    }

                    return { valid: true, status: 'valid' as const };
                }

                return { valid: true, status: 'valid' as const };
            }
        }
    ];
}

function getRoomColumns(refDataService: ReferenceDataService): ValidatedColDef[] {
    return [
        {
            field: 'name',
            headerName: 'Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        },
        {
            field: 'capacity',
            headerName: 'Capacity',
            editable: true,
            width: 100,
            validator: positiveIntValidator
        },
        {
            field: 'zone_name',
            headerName: 'Zone',
            editable: true,
            width: 150,
            validator: createForeignKeyValidator(refDataService, 'zone'),
            referenceCheck: true,
            autocompleteValues: () => refDataService.getZoneNames()
        },
        {
            field: 'features',
            headerName: 'Features',
            editable: true,
            flex: 1,
            validator: createForeignKeyValidator(refDataService, 'feature', true),
            referenceCheck: true,
            autocompleteValues: () => refDataService.getFeatureNames(),
            tooltipValueGetter: () => 'Separate multiple features with |',
            supportsMultiple: true
        }
    ];
}

function getZoneColumns(): ValidatedColDef[] {
    return [
        {
            field: 'name',
            headerName: 'Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        }
    ];
}

function getFeatureColumns(): ValidatedColDef[] {
    return [
        {
            field: 'name',
            headerName: 'Name',
            editable: true,
            flex: 1,
            validator: requiredValidator
        }
    ];
}



/**
 * Get CSV headers for an entity type.
 */
export function getCSVHeaders(entityType: EntityType): string[] {
    switch (entityType) {
        case 'lecturers':
            return ['name', 'email'];
        case 'courses':
            return ['code', 'name', 'weekly_hours', 'lecturer_email', 'student_group_names', 'is_online', 'required_features', 'allowed_zones'];
        case 'student-groups':
            return ['base_name', 'is_parent', 'level', 'group', 'size', 'parent_group_name'];
        case 'rooms':
            return ['name', 'capacity', 'zone_name', 'features'];
        case 'zones':
            return ['name'];
        case 'features':
            return ['name'];
        case 'users':
            return ['email', 'first_name', 'last_name', 'role', 'department', 'phone'];
        default:
            return [];
    }
}

/**
 * Create an empty row with all fields initialized.
 */
export function createEmptyRow(entityType: EntityType): Record<string, string> {
    const headers = getCSVHeaders(entityType);
    const row: Record<string, string> = {};
    headers.forEach(h => row[h] = '');
    return row;
}
