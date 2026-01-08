import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Auth guard to protect routes from unauthenticated users.
 */
export const authGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
        router.navigate(['/login'], {
            queryParams: { returnUrl: route.url.join('/') }
        });
        return false;
    }

    // Check for required roles if specified
    const requiredRoles = route.data['roles'] as string[] | undefined;

    if (requiredRoles && requiredRoles.length > 0) {
        if (!authService.hasAnyRole(requiredRoles)) {
            // User doesn't have required role - redirect to home
            router.navigate(['/dashboard']);
            return false;
        }
    }

    return true;
};

/**
 * Admin guard - only allows SUPER_ADMIN and ADMIN.
 */
export const adminGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
        router.navigate(['/login']);
        return false;
    }

    if (!authService.isAdmin()) {
        router.navigate(['/dashboard']);
        return false;
    }

    return true;
};

/**
 * Coordinator guard - allows SUPER_ADMIN, ADMIN, and COORDINATOR.
 */
export const coordinatorGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
        router.navigate(['/login']);
        return false;
    }

    if (!authService.isCoordinator()) {
        router.navigate(['/dashboard']);
        return false;
    }

    return true;
};

/**
 * Guest guard - only allows unauthenticated users (for login page).
 */
export const guestGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.isAuthenticated()) {
        router.navigate(['/dashboard']);
        return false;
    }

    return true;
};
