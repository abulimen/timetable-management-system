package com.university.timetable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.ortools.Loader;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Google OR-Tools CP-SAT solver.
 * 
 * Note: OR-Tools requires native libraries to be loaded.
 * The ortools-java Maven dependency includes native libraries for Linux x86-64.
 * For other platforms, you may need to add platform-specific dependencies.
 */
@Configuration
@Slf4j
public class CpSatConfig {

    @Value("${cpsat.solver.time-limit-seconds:60}")
    private int timeLimitSeconds;

    @Value("${cpsat.solver.num-workers:8}")
    private int numWorkers;

    @Value("${cpsat.solver.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        try {
            Loader.loadNativeLibraries();
            log.info("OR-Tools native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            log.warn("OR-Tools native libraries not found. CP-SAT solver will be disabled. Error: {}", 
                    e.getMessage());
        }
    }

    @Bean
    public CpSatSettings cpSatSettings() {
        return new CpSatSettings(timeLimitSeconds, numWorkers, enabled);
    }

    public record CpSatSettings(int timeLimitSeconds, int numWorkers, boolean enabled) {}
}
