export type QueryOperator =
    | 'eq'
    | 'neq'
    | 'contains'
    | 'startsWith'
    | 'endsWith'
    | 'in'
    | 'notIn'
    | 'gt'
    | 'gte'
    | 'lt'
    | 'lte'
    | 'between'
    | 'isNull'
    | 'isNotNull';

export type QueryDirection = 'asc' | 'desc';
export type QueryMatchMode = 'all' | 'any';

export interface QueryFilter<TValue = unknown> {
    field: string;
    operator: QueryOperator;
    value?: TValue;
    exclude?: boolean;
}

export interface QuerySort {
    field: string;
    direction: QueryDirection;
}

export interface QueryPagination {
    page: number;
    size: number;
}

export interface QueryColumnState {
    key: string;
    visible: boolean;
}

export interface DataQueryState {
    search: string;
    filters: QueryFilter[];
    sort: QuerySort[];
    matchMode: QueryMatchMode;
    pagination: QueryPagination;
    columns: QueryColumnState[];
}

export const DEFAULT_QUERY_STATE: DataQueryState = {
    search: '',
    filters: [],
    sort: [],
    matchMode: 'all',
    pagination: {
        page: 1,
        size: 25
    },
    columns: []
};
