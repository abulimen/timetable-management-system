package com.university.timetable.solver;

import com.university.timetable.domain.Timeslot;
import java.util.Comparator;

/**
 * Strength comparator for Timeslot values.
 * 
 * Earlier timeslots (morning, early week) are considered "stronger".
 * Used by WEAKEST_FIT to prefer later/afternoon slots first.
 */
public class TimeslotStrengthComparator implements Comparator<Timeslot> {

    @Override
    public int compare(Timeslot a, Timeslot b) {
        // Earlier day = stronger
        int dayCompare = Integer.compare(a.getDayOfWeek().getValue(), b.getDayOfWeek().getValue());
        if (dayCompare != 0) {
            return dayCompare;
        }
        
        // Earlier time = stronger
        return a.getStartTime().compareTo(b.getStartTime());
    }
}
