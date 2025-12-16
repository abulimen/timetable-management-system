package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Timeslot;
import org.optaplanner.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

/**
 * Nearby distance meter for Timeslots.
 * 
 * Measures "distance" between timeslots to enable nearby selection optimization.
 * Closer timeslots (same day, nearby hours) are preferred for moves.
 * 
 * Distance calculation:
 * - Same day: difference in hours
 * - Different days: day difference * 24 + hour difference
 */
public class TimeslotNearbyDistanceMeter implements NearbyDistanceMeter<Lesson, Timeslot> {

    @Override
    public double getNearbyDistance(Lesson origin, Timeslot destination) {
        Timeslot originTimeslot = origin.getTimeslot();
        
        // If origin has no timeslot, all destinations are equally far
        if (originTimeslot == null) {
            return Double.MAX_VALUE;
        }
        
        // Same timeslot = distance 0
        if (originTimeslot.equals(destination)) {
            return 0.0;
        }
        
        int originDay = originTimeslot.getDayOfWeek().getValue();
        int destDay = destination.getDayOfWeek().getValue();
        int originHour = originTimeslot.getStartTime().getHour();
        int destHour = destination.getStartTime().getHour();
        
        // Same day: just the hour difference
        if (originDay == destDay) {
            return Math.abs(originHour - destHour);
        }
        
        // Different day: penalize by day distance + hour difference
        int dayDiff = Math.abs(originDay - destDay);
        int hourDiff = Math.abs(originHour - destHour);
        
        // Weight: each day difference is like 12 hours of distance
        return dayDiff * 12.0 + hourDiff;
    }
}
