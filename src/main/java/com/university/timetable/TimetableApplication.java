package com.university.timetable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * University Timetable Scheduling Engine
 * 
 * An enterprise-grade automated scheduling system using OptaPlanner
 * for constraint-based optimization of university course timetables.
 */
@SpringBootApplication
public class TimetableApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimetableApplication.class, args);
    }
}
