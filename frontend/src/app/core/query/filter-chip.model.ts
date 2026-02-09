import { QueryFilter } from './query-state.model';

export interface FilterChip {
    id: string;
    label: string;
    removable: boolean;
    field: string;
    operator: QueryFilter['operator'];
    exclude: boolean;
}

export interface BuildFilterChipOptions {
    id?: string;
    label?: string;
    removable?: boolean;
}

export function buildFilterChip(filter: QueryFilter, options: BuildFilterChipOptions = {}): FilterChip {
    return {
        id: options.id ?? `${filter.field}:${filter.operator}:${stableValue(filter.value)}`,
        label: options.label ?? `${filter.field} ${filter.operator}${formatValueSuffix(filter.value)}`,
        removable: options.removable ?? true,
        field: filter.field,
        operator: filter.operator,
        exclude: !!filter.exclude
    };
}

function formatValueSuffix(value: unknown): string {
    if (value === undefined || value === null) {
        return '';
    }
    if (Array.isArray(value)) {
        return ` [${value.map(v => String(v)).join(', ')}]`;
    }
    if (typeof value === 'object') {
        return ` ${JSON.stringify(value)}`;
    }
    return ` ${String(value)}`;
}

function stableValue(value: unknown): string {
    if (value === undefined) {
        return 'undefined';
    }
    if (value === null) {
        return 'null';
    }
    if (typeof value !== 'object') {
        return String(value);
    }
    try {
        return JSON.stringify(value);
    } catch {
        return 'unserializable';
    }
}
