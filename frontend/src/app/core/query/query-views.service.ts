import { Injectable } from '@angular/core';

export interface SavedQueryView<TPayload = unknown> {
    id: string;
    name: string;
    payload: TPayload;
    updatedAt: string;
}

@Injectable({
    providedIn: 'root'
})
export class QueryViewsService {
    private readonly prefix = 'queryViews:';

    list<TPayload>(pageKey: string): SavedQueryView<TPayload>[] {
        return this.read<TPayload>(pageKey);
    }

    save<TPayload>(pageKey: string, name: string, payload: TPayload): SavedQueryView<TPayload> {
        const views = this.read<TPayload>(pageKey);
        const now = new Date().toISOString();
        const normalizedName = name.trim();
        const existing = views.find(v => v.name.toLowerCase() === normalizedName.toLowerCase());

        if (existing) {
            existing.payload = payload;
            existing.updatedAt = now;
            this.write(pageKey, views);
            return existing;
        }

        const created: SavedQueryView<TPayload> = {
            id: this.createId(),
            name: normalizedName,
            payload,
            updatedAt: now
        };
        views.push(created);
        this.write(pageKey, views);
        return created;
    }

    delete(pageKey: string, viewId: string): void {
        const views = this.read(pageKey).filter(v => v.id !== viewId);
        this.write(pageKey, views);
    }

    private read<TPayload>(pageKey: string): SavedQueryView<TPayload>[] {
        const raw = localStorage.getItem(this.storageKey(pageKey));
        if (!raw) {
            return [];
        }
        try {
            const parsed: unknown = JSON.parse(raw);
            return Array.isArray(parsed) ? (parsed as SavedQueryView<TPayload>[]) : [];
        } catch {
            return [];
        }
    }

    private write<TPayload>(pageKey: string, views: SavedQueryView<TPayload>[]): void {
        localStorage.setItem(this.storageKey(pageKey), JSON.stringify(views));
    }

    private storageKey(pageKey: string): string {
        return `${this.prefix}${pageKey}`;
    }

    private createId(): string {
        return `view_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    }
}
