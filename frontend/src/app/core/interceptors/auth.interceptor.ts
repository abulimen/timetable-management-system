import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError, BehaviorSubject, filter, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

// Track if we're currently refreshing
let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

/**
 * Auth interceptor to attach JWT token to requests and handle 401 errors.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
    const authService = inject(AuthService);

    // Skip auth for login/refresh endpoints
    if (req.url.includes('/api/v1/auth/login') || req.url.includes('/api/v1/auth/refresh')) {
        return next(req);
    }

    const token = authService.getAccessToken();

    if (token) {
        req = addTokenToRequest(req, token);
    }

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            // Handle 401 - try to refresh token
            if (error.status === 401 && !req.url.includes('/api/v1/auth/')) {
                return handleTokenExpired(req, next, authService);
            }
            // Handle 403 - access denied, session likely expired
            if (error.status === 403 && !req.url.includes('/api/v1/auth/')) {
                authService.logout();
                return throwError(() => error);
            }
            return throwError(() => error);
        })
    );
};

/**
 * Add Bearer token to request headers.
 */
function addTokenToRequest(request: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
    return request.clone({
        setHeaders: {
            Authorization: `Bearer ${token}`
        }
    });
}

/**
 * Handle token expiration by refreshing and retrying the request.
 */
function handleTokenExpired(
    request: HttpRequest<unknown>,
    next: HttpHandlerFn,
    authService: AuthService
) {
    if (!isRefreshing) {
        isRefreshing = true;
        refreshTokenSubject.next(null);

        return authService.refreshToken().pipe(
            switchMap(response => {
                isRefreshing = false;
                refreshTokenSubject.next(response.accessToken);
                return next(addTokenToRequest(request, response.accessToken));
            }),
            catchError(error => {
                isRefreshing = false;
                authService.logout();
                return throwError(() => error);
            })
        );
    }

    // Wait for refresh to complete and retry
    return refreshTokenSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => next(addTokenToRequest(request, token!)))
    );
}
