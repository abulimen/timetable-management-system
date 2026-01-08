import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';
import { Router } from '@angular/router';

// Interfaces
export interface User {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: 'SUPER_ADMIN' | 'ADMIN' | 'COORDINATOR' | 'LECTURER' | 'VIEWER';
    lecturerId?: number;
}

export interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    user: User;
}

export interface LoginRequest {
    email: string;
    password: string;
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly API_URL = 'http://localhost:8080/api/auth';
    private readonly ACCESS_TOKEN_KEY = 'access_token';
    private readonly REFRESH_TOKEN_KEY = 'refresh_token';
    private readonly USER_KEY = 'current_user';

    private currentUserSubject = new BehaviorSubject<User | null>(null);
    public currentUser$ = this.currentUserSubject.asObservable();

    private tokenExpirationTimer: any;

    constructor(
        private http: HttpClient,
        private router: Router
    ) {
        this.loadStoredUser();
    }

    /**
     * Load user from localStorage on app init.
     */
    private loadStoredUser(): void {
        const storedUser = localStorage.getItem(this.USER_KEY);
        const accessToken = localStorage.getItem(this.ACCESS_TOKEN_KEY);

        if (storedUser && accessToken) {
            try {
                const user = JSON.parse(storedUser);
                this.currentUserSubject.next(user);
                this.setupTokenRefresh();
            } catch {
                this.clearTokens();
            }
        }
    }

    /**
     * Login with email and password.
     */
    login(credentials: LoginRequest): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(`${this.API_URL}/login`, credentials).pipe(
            tap(response => {
                this.handleLoginResponse(response);
            }),
            catchError(this.handleError)
        );
    }

    /**
     * Handle successful login response.
     */
    private handleLoginResponse(response: LoginResponse): void {
        localStorage.setItem(this.ACCESS_TOKEN_KEY, response.accessToken);
        localStorage.setItem(this.REFRESH_TOKEN_KEY, response.refreshToken);
        localStorage.setItem(this.USER_KEY, JSON.stringify(response.user));
        this.currentUserSubject.next(response.user);
        this.setupTokenRefresh(response.expiresIn);
    }

    /**
     * Setup automatic token refresh before expiration.
     */
    private setupTokenRefresh(expiresInSeconds?: number): void {
        if (this.tokenExpirationTimer) {
            clearTimeout(this.tokenExpirationTimer);
        }

        // Refresh 1 minute before expiration (default 14 minutes if not specified)
        const refreshIn = ((expiresInSeconds || 900) - 60) * 1000;

        if (refreshIn > 0) {
            this.tokenExpirationTimer = setTimeout(() => {
                this.refreshToken().subscribe();
            }, refreshIn);
        }
    }

    /**
     * Refresh access token.
     */
    refreshToken(): Observable<LoginResponse> {
        const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);

        if (!refreshToken) {
            this.logout();
            return throwError(() => new Error('No refresh token available'));
        }

        return this.http.post<LoginResponse>(`${this.API_URL}/refresh`, { refreshToken }).pipe(
            tap(response => {
                this.handleLoginResponse(response);
            }),
            catchError(error => {
                console.error('Token refresh failed:', error);
                this.logout();
                return throwError(() => error);
            })
        );
    }

    /**
     * Logout user.
     */
    logout(): void {
        const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);

        if (refreshToken) {
            // Notify server to invalidate token
            this.http.post(`${this.API_URL}/logout`, { refreshToken }).subscribe({
                error: () => { } // Ignore errors on logout
            });
        }

        this.clearTokens();
        this.router.navigate(['/login']);
    }

    /**
     * Clear all stored tokens and user data.
     */
    private clearTokens(): void {
        localStorage.removeItem(this.ACCESS_TOKEN_KEY);
        localStorage.removeItem(this.REFRESH_TOKEN_KEY);
        localStorage.removeItem(this.USER_KEY);
        this.currentUserSubject.next(null);

        if (this.tokenExpirationTimer) {
            clearTimeout(this.tokenExpirationTimer);
        }
    }

    /**
     * Get current access token.
     */
    getAccessToken(): string | null {
        return localStorage.getItem(this.ACCESS_TOKEN_KEY);
    }

    /**
     * Check if user is authenticated.
     */
    isAuthenticated(): boolean {
        return !!this.getAccessToken() && !!this.currentUserSubject.value;
    }

    /**
     * Get current user.
     */
    getCurrentUser(): User | null {
        return this.currentUserSubject.value;
    }

    /**
     * Check if current user has specific role.
     */
    hasRole(role: string): boolean {
        const user = this.currentUserSubject.value;
        return user?.role === role;
    }

    /**
     * Check if current user has any of the specified roles.
     */
    hasAnyRole(roles: string[]): boolean {
        const user = this.currentUserSubject.value;
        return !!user && roles.includes(user.role);
    }

    /**
     * Check if current user is admin or higher.
     */
    isAdmin(): boolean {
        return this.hasAnyRole(['SUPER_ADMIN', 'ADMIN']);
    }

    /**
     * Check if current user is coordinator or higher.
     */
    isCoordinator(): boolean {
        return this.hasAnyRole(['SUPER_ADMIN', 'ADMIN', 'COORDINATOR']);
    }

    /**
     * Get current user's role level (lower = more permissions).
     */
    getRoleLevel(): number {
        const user = this.currentUserSubject.value;
        if (!user) return 999;

        const levels: Record<string, number> = {
            'SUPER_ADMIN': 0,
            'ADMIN': 1,
            'COORDINATOR': 2,
            'LECTURER': 3,
            'VIEWER': 4
        };
        return levels[user.role] ?? 999;
    }

    /**
     * Handle HTTP errors.
     */
    private handleError(error: HttpErrorResponse): Observable<never> {
        let message = 'An error occurred';

        if (error.error?.message) {
            message = error.error.message;
        } else if (error.status === 401) {
            message = 'Invalid email or password';
        } else if (error.status === 423) {
            message = 'Account is locked. Please try again later.';
        } else if (error.status === 0) {
            message = 'Cannot connect to server';
        }

        return throwError(() => new Error(message));
    }
}
