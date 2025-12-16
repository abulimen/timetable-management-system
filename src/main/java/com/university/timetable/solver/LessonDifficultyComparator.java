package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;

import java.util.Comparator;

/**
 * Difficulty comparator for Lesson entities.
 * 
 * Used with FIRST_FIT_DECREASING construction heuristic.
 * Harder-to-schedule lessons are placed first, leaving easier ones for later.
 * 
 * Difficulty factors (in order of importance):
 * 1. Duration: 2-hour lessons are harder to place than 1-hour
 * 2. Required features: More features = more constrained
 * 3. Allowed zones: Fewer zones = more constrained
 */
public class LessonDifficultyComparator implements Comparator<Lesson> {

    @Override
    public int compare(Lesson a, Lesson b) {
        // Higher difficulty = should be scheduled first
        return Integer.compare(getDifficulty(b), getDifficulty(a));
    }
    
    private int getDifficulty(Lesson lesson) {
        int difficulty = 0;
        
        // 2-hour lessons are harder to place (fewer valid timeslots)
        difficulty += lesson.getDurationHours() * 100;
        
        // More required features = more constrained
        if (lesson.getCourse() != null && lesson.getCourse().getRequiredFeatures() != null) {
            difficulty += lesson.getCourse().getRequiredFeatures().size() * 50;
        }
        
        // Fewer allowed zones = more constrained
        if (lesson.getCourse() != null && lesson.getCourse().getAllowedZones() != null) {
            int zones = lesson.getCourse().getAllowedZones().size();
            if (zones > 0 && zones < 10) {
                difficulty += (10 - zones) * 20;  // Fewer zones = higher difficulty
            }
        }
        
        // Pinned lessons have zero difficulty (already assigned)
        if (lesson.isPinned()) {
            return 0;
        }
        
        return difficulty;
    }
}
