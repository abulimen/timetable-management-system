import { ReferenceDataService } from '../services/reference-data.service';

/**
 * Validation result for a cell.
 */
export interface CellValidation {
    valid: boolean;
    status: 'valid' | 'warning' | 'error';
    message?: string;
}

/**
 * Validator function type.
 * @param value - The cell value to validate
 * @param rowData - Optional: The entire row data as an object
 * @param allRows - Optional: All rows in the spreadsheet (for cross-row validation)
 */
export type CellValidator = (value: any, rowData?: any, allRows?: any[]) => CellValidation;

// Email regex pattern
const EMAIL_PATTERN = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/;

/**
 * Validates that a value is not empty.
 */
export function requiredValidator(value: any): CellValidation {
    if (value === null || value === undefined || String(value).trim() === '') {
        return { valid: false, status: 'error', message: 'This field is required' };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates email format.
 */
export function emailValidator(value: any): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: true, status: 'valid' }; // Empty is OK if not required
    }
    const email = String(value).trim();
    if (!EMAIL_PATTERN.test(email)) {
        return { valid: false, status: 'error', message: 'Invalid email format' };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates a positive integer.
 */
export function positiveIntValidator(value: any, maxValue: number = 10000): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: true, status: 'valid' }; // Empty is OK if not required
    }
    const num = parseInt(String(value), 10);
    if (isNaN(num)) {
        return { valid: false, status: 'error', message: 'Must be a number' };
    }
    if (num <= 0) {
        return { valid: false, status: 'error', message: 'Must be greater than 0' };
    }
    if (num > maxValue) {
        return { valid: false, status: 'error', message: `Must be at most ${maxValue}` };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates a non-negative integer (allows 0).
 */
export function nonNegativeIntValidator(value: any, maxValue: number = 10000): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: true, status: 'valid' };
    }
    const num = parseInt(String(value), 10);
    if (isNaN(num)) {
        return { valid: false, status: 'error', message: 'Must be a number' };
    }
    if (num < 0) {
        return { valid: false, status: 'error', message: 'Must be 0 or greater' };
    }
    if (num > maxValue) {
        return { valid: false, status: 'error', message: `Must be at most ${maxValue}` };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates a boolean value.
 */
export function booleanValidator(value: any): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: true, status: 'valid' };
    }
    const val = String(value).trim().toLowerCase();
    const validValues = ['true', 'false', 'yes', 'no', '1', '0'];
    if (!validValues.includes(val)) {
        return { valid: false, status: 'error', message: 'Must be true/false, yes/no, or 1/0' };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates course code format (alphanumeric, uppercase).
 */
export function courseCodeValidator(value: any): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: false, status: 'error', message: 'Course code is required' };
    }
    const code = String(value).trim();
    if (code.length > 20) {
        return { valid: false, status: 'error', message: 'Code must be at most 20 characters' };
    }
    // Allow letters, numbers, spaces, hyphens, underscores
    if (!/^[A-Za-z0-9\s\-_]+$/.test(code)) {
        return { valid: false, status: 'error', message: 'Code must be alphanumeric' };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates user role enum.
 */
export function roleValidator(value: any): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: false, status: 'error', message: 'Role is required' };
    }
    const role = String(value).trim().toUpperCase();
    const validRoles = ['ADMIN', 'COORDINATOR', 'LECTURER', 'VIEWER'];
    if (!validRoles.includes(role)) {
        return { valid: false, status: 'error', message: `Valid roles: ${validRoles.join(', ')}` };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Validates phone format.
 * Allows digits, spaces, +, -, (, )
 */
export function phoneValidator(value: any): CellValidation {
    if (!value || String(value).trim() === '') {
        return { valid: true, status: 'valid' }; // Optional field
    }
    const phone = String(value).trim();
    if (!/^[0-9+\-()\s]+$/.test(phone)) {
        return { valid: false, status: 'error', message: 'Phone contains invalid characters' };
    }
    const digitsOnly = phone.replace(/\D/g, '');
    if (digitsOnly.length < 7 || digitsOnly.length > 15) {
        return { valid: false, status: 'error', message: 'Phone must have 7-15 digits' };
    }
    return { valid: true, status: 'valid' };
}

/**
 * Creates a validator that checks if a value exists in reference data.
 */
export function createForeignKeyValidator(
    refDataService: ReferenceDataService,
    type: 'lecturer' | 'studentGroup' | 'zone' | 'feature',
    allowMultiple: boolean = false
): CellValidator {
    return (value: any): CellValidation => {
        if (!value || String(value).trim() === '') {
            return { valid: true, status: 'valid' }; // Empty is OK if not required
        }

        const val = String(value).trim();

        if (allowMultiple) {
            // Pipe-separated values
            const values = val.split('|').map(v => v.trim()).filter(v => v);
            const missing: string[] = [];

            for (const v of values) {
                let exists = false;
                switch (type) {
                    case 'lecturer':
                        exists = refDataService.hasLecturerEmail(v);
                        break;
                    case 'studentGroup':
                        exists = refDataService.hasStudentGroup(v);
                        break;
                    case 'zone':
                        exists = refDataService.hasZone(v);
                        break;
                    case 'feature':
                        exists = refDataService.hasFeature(v);
                        break;
                }
                if (!exists) {
                    missing.push(v);
                }
            }

            if (missing.length > 0) {
                return {
                    valid: false,
                    status: 'error',
                    message: `Not found: ${missing.join(', ')}`
                };
            }
            return { valid: true, status: 'valid' };
        } else {
            // Single value
            let exists = false;
            switch (type) {
                case 'lecturer':
                    exists = refDataService.hasLecturerEmail(val);
                    break;
                case 'studentGroup':
                    exists = refDataService.hasStudentGroup(val);
                    break;
                case 'zone':
                    exists = refDataService.hasZone(val);
                    break;
                case 'feature':
                    exists = refDataService.hasFeature(val);
                    break;
            }

            if (!exists) {
                return {
                    valid: false,
                    status: 'error',
                    message: `${type} '${val}' not found`
                };
            }
            return { valid: true, status: 'valid' };
        }
    };
}

/**
 * Combines multiple validators into one.
 */
export function combineValidators(...validators: CellValidator[]): CellValidator {
    return (value: any, rowData?: any, allRows?: any[]): CellValidation => {
        for (const validator of validators) {
            const result = validator(value, rowData, allRows);
            if (!result.valid) {
                return result;
            }
        }
        return { valid: true, status: 'valid' };
    };
}
