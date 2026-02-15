package com.university.timetable.config;

import com.university.timetable.service.TimetableChangeTrackerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class TimetableEditLockInterceptor implements HandlerInterceptor {

    private final TimetableChangeTrackerService timetableChangeTrackerService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!isMutating(request)) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        if (isAlwaysAllowedMutatingPath(path)) {
            return true;
        }
        if (timetableChangeTrackerService.isEditingEnabled()) {
            return true;
        }

        response.setStatus(423);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"TIMETABLE_LOCKED\",\"message\":\"Editing is disabled after timetable generation. Enable editing mode first from Solver page. Any changes will require FULL_REPLAN.\"}");
        return false;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        if (!isMutating(request) || ex != null || response.getStatus() >= 400) {
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return;
        }
        if (isSkippedForDirtyTracking(path) || !timetableChangeTrackerService.isEditingEnabled()) {
            return;
        }

        timetableChangeTrackerService.markDirty(
                "Data changed while editing mode is enabled. Run Solver FULL_REPLAN to regenerate timetable.");
    }

    private boolean isMutating(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isAlwaysAllowedMutatingPath(String path) {
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/solver/");
    }

    private boolean isSkippedForDirtyTracking(String path) {
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/solver/");
    }
}
