package com.university.timetable.solver;

import com.university.timetable.domain.Room;
import java.util.Comparator;

/**
 * Strength comparator for Room values.
 * 
 * Higher capacity rooms are considered "stronger".
 * Used by WEAKEST_FIT to prefer smaller rooms (preserving larger ones).
 */
public class RoomStrengthComparator implements Comparator<Room> {

    @Override
    public int compare(Room a, Room b) {
        // Higher capacity = stronger
        return Integer.compare(a.getCapacity(), b.getCapacity());
    }
}
