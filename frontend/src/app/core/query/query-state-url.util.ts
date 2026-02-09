import {
    DataQueryState,
    DEFAULT_QUERY_STATE,
    QueryColumnState,
    QueryFilter,
    QueryMatchMode,
    QuerySort
} from './query-state.model';

export interface QueryParamMap {
    [key: string]: string | undefined;
}

export function normalizeQueryState(
    state: Partial<DataQueryState>,
    defaults: DataQueryState = DEFAULT_QUERY_STATE
): DataQueryState {
    const merged: DataQueryState = {
        search: state.search ?? defaults.search,
        filters: state.filters ?? defaults.filters,
        sort: state.sort ?? defaults.sort,
        matchMode: state.matchMode ?? defaults.matchMode,
        pagination: state.pagination ?? defaults.pagination,
        columns: state.columns ?? defaults.columns
    };

    return {
        search: merged.search.trim(),
        filters: sanitizeFilters(merged.filters),
        sort: sanitizeSort(merged.sort),
        matchMode: merged.matchMode === 'any' ? 'any' : 'all',
        pagination: {
            page: Math.max(1, Math.floor(merged.pagination.page || defaults.pagination.page)),
            size: Math.max(1, Math.floor(merged.pagination.size || defaults.pagination.size))
        },
        columns: sanitizeColumns(merged.columns)
    };
}

export function serializeQueryStateToParams(
    state: Partial<DataQueryState>,
    defaults: DataQueryState = DEFAULT_QUERY_STATE
): URLSearchParams {
    const normalized = normalizeQueryState(state, defaults);
    const params = new URLSearchParams();

    if (normalized.search) {
        params.set('q', normalized.search);
    }

    if (normalized.matchMode !== defaults.matchMode) {
        params.set('match', normalized.matchMode);
    }

    if (normalized.pagination.page !== defaults.pagination.page) {
        params.set('page', String(normalized.pagination.page));
    }

    if (normalized.pagination.size !== defaults.pagination.size) {
        params.set('size', String(normalized.pagination.size));
    }

    if (normalized.filters.length > 0) {
        params.set('filters', safeStringify(normalized.filters));
    }

    if (normalized.sort.length > 0) {
        params.set('sort', safeStringify(normalized.sort));
    }

    if (normalized.columns.length > 0) {
        params.set('columns', safeStringify(normalized.columns));
    }

    return params;
}

export function serializeQueryStateToString(
    state: Partial<DataQueryState>,
    defaults: DataQueryState = DEFAULT_QUERY_STATE
): string {
    return serializeQueryStateToParams(state, defaults).toString();
}

export function parseQueryStateFromParams(
    paramsInput: URLSearchParams | QueryParamMap | string,
    defaults: DataQueryState = DEFAULT_QUERY_STATE
): DataQueryState {
    const params = asSearchParams(paramsInput);

    const parsed: Partial<DataQueryState> = {
        search: params.get('q') ?? defaults.search,
        matchMode: parseMatchMode(params.get('match')),
        pagination: {
            page: parseNumber(params.get('page'), defaults.pagination.page),
            size: parseNumber(params.get('size'), defaults.pagination.size)
        },
        filters: parseJsonArray<QueryFilter>(params.get('filters')),
        sort: parseJsonArray<QuerySort>(params.get('sort')),
        columns: parseJsonArray<QueryColumnState>(params.get('columns'))
    };

    return normalizeQueryState(parsed, defaults);
}

export function mergeQueryState(
    state: DataQueryState,
    patch: Partial<DataQueryState>,
    defaults: DataQueryState = DEFAULT_QUERY_STATE
): DataQueryState {
    return normalizeQueryState(
        {
            ...state,
            ...patch,
            pagination: patch.pagination ? { ...state.pagination, ...patch.pagination } : state.pagination
        },
        defaults
    );
}

function sanitizeFilters(filters: QueryFilter[]): QueryFilter[] {
    return (filters || [])
        .filter(filter => !!filter && typeof filter.field === 'string' && filter.field.trim().length > 0)
        .map(filter => ({
            field: filter.field.trim(),
            operator: filter.operator,
            value: filter.value,
            exclude: !!filter.exclude
        }));
}

function sanitizeSort(sort: QuerySort[]): QuerySort[] {
    return (sort || [])
        .filter(item => !!item && typeof item.field === 'string' && item.field.trim().length > 0)
        .map(item => ({
            field: item.field.trim(),
            direction: item.direction === 'desc' ? 'desc' : 'asc'
        }));
}

function sanitizeColumns(columns: QueryColumnState[]): QueryColumnState[] {
    return (columns || [])
        .filter(column => !!column && typeof column.key === 'string' && column.key.trim().length > 0)
        .map(column => ({
            key: column.key.trim(),
            visible: column.visible !== false
        }));
}

function safeStringify(value: unknown): string {
    try {
        return JSON.stringify(value);
    } catch {
        return '[]';
    }
}

function parseJsonArray<T>(value: string | null): T[] {
    if (!value) {
        return [];
    }
    try {
        const parsed: unknown = JSON.parse(value);
        return Array.isArray(parsed) ? (parsed as T[]) : [];
    } catch {
        return [];
    }
}

function parseNumber(value: string | null, fallback: number): number {
    if (!value) {
        return fallback;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function parseMatchMode(value: string | null): QueryMatchMode {
    return value === 'any' ? 'any' : 'all';
}

function asSearchParams(input: URLSearchParams | QueryParamMap | string): URLSearchParams {
    if (input instanceof URLSearchParams) {
        return input;
    }
    if (typeof input === 'string') {
        const source = input.startsWith('?') ? input.slice(1) : input;
        return new URLSearchParams(source);
    }

    const params = new URLSearchParams();
    Object.entries(input).forEach(([key, value]) => {
        if (value !== undefined) {
            params.set(key, value);
        }
    });
    return params;
}
