package com.university.timetable.config;

import com.university.timetable.util.AuditRequestContext;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;

/**
 * Async execution configuration that preserves security/request context
 * for @Async methods (used by audit logging).
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("butms-async-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            // No-op: audit/logging paths already guard errors internally.
        };
    }

    private static class ContextCopyingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            SecurityContext securityContext = SecurityContextHolder.getContext();
            AuditRequestContext.Snapshot capturedAuditSnapshot = AuditRequestContext.captureSnapshot();

            return () -> {
                RequestAttributes previousRequestAttributes = RequestContextHolder.getRequestAttributes();
                SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
                AuditRequestContext.Snapshot previousAuditSnapshot = AuditRequestContext.getSnapshot();
                try {
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }
                    SecurityContextHolder.setContext(securityContext);
                    AuditRequestContext.setSnapshot(capturedAuditSnapshot);
                    runnable.run();
                } finally {
                    if (previousRequestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(previousRequestAttributes);
                    } else {
                        RequestContextHolder.resetRequestAttributes();
                    }
                    SecurityContextHolder.setContext(previousSecurityContext);
                    if (previousAuditSnapshot != null) {
                        AuditRequestContext.setSnapshot(previousAuditSnapshot);
                    } else {
                        AuditRequestContext.clearSnapshot();
                    }
                }
            };
        }
    }
}
