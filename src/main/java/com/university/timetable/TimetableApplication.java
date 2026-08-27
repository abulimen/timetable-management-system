package com.university.timetable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * University Timetable Scheduling Engine
 * 
 * An automated scheduling system using Timefold Solver and Google OR-Tools CP-SAT
 * for constraint-based optimization of university course timetables.
 */
@SpringBootApplication
@EnableAsync
public class TimetableApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimetableApplication.class, args);
    }
}
