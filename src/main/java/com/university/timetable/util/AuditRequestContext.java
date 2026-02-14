package com.university.timetable.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Utility class for capturing audit request context.
 */
public class AuditRequestContext {

    public static final String REQUEST_ID_ATTRIBUTE = "auditRequestId";
    private static final ThreadLocal<Snapshot> SNAPSHOT_HOLDER = new ThreadLocal<>();

    private AuditRequestContext() {
        // Utility class
    }

    /**
     * Get the current user's ID from security context.
     */
    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Get the client IP address from the current request.
     */
    public static String getClientIpAddress() {
        Snapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot != null && snapshot.clientIpAddress() != null && !snapshot.clientIpAddress().isBlank()) {
            return snapshot.clientIpAddress();
        }

        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }

        // Check for forwarded IP (behind proxy/load balancer)
        String xForwardedFor = safeGetHeader(request, "X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP if there are multiple
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = safeGetHeader(request, "X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return safeGetRemoteAddr(request);
    }

    /**
     * Get the session ID from the current request.
     */
    public static String getSessionId() {
        Snapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot != null && snapshot.sessionId() != null && !snapshot.sessionId().isBlank()) {
            return snapshot.sessionId();
        }

        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            try {
                if (request.getSession(false) != null) {
                    return request.getSession(false).getId();
                }
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get request correlation ID.
     * Reuses X-Request-ID header if provided; otherwise generates once per request.
     */
    public static String getOrCreateRequestId() {
        Snapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot != null && snapshot.requestId() != null && !snapshot.requestId().isBlank()) {
            return snapshot.requestId();
        }

        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return UUID.randomUUID().toString().substring(0, 8);
        }

        Object existing;
        try {
            existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        } catch (IllegalStateException ignored) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        if (existing instanceof String existingId && !existingId.isBlank()) {
            return existingId;
        }

        String externalRequestId = safeGetHeader(request, "X-Request-ID");
        String requestId = (externalRequestId != null && !externalRequestId.isBlank())
                ? externalRequestId.trim()
                : UUID.randomUUID().toString().substring(0, 8);
        try {
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        } catch (IllegalStateException ignored) {
            // Request already recycled; fallback to generated id.
        }
        return requestId;
    }

    /**
     * Capture request context once on the request thread and use it in async execution.
     */
    public static Snapshot captureSnapshot() {
        return new Snapshot(getClientIpAddress(), getSessionId(), getOrCreateRequestId());
    }

    public static Snapshot getSnapshot() {
        return SNAPSHOT_HOLDER.get();
    }

    public static void setSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            SNAPSHOT_HOLDER.remove();
        } else {
            SNAPSHOT_HOLDER.set(snapshot);
        }
    }

    public static void clearSnapshot() {
        SNAPSHOT_HOLDER.remove();
    }

    /**
     * Get the current HTTP request.
     */
    private static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest();
            }
        } catch (Exception e) {
            // Not in a web request context
        }
        return null;
    }

    private static String safeGetHeader(HttpServletRequest request, String headerName) {
        try {
            return request.getHeader(headerName);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static String safeGetRemoteAddr(HttpServletRequest request) {
        try {
            return request.getRemoteAddr();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    public record Snapshot(String clientIpAddress, String sessionId, String requestId) {
    }
}
