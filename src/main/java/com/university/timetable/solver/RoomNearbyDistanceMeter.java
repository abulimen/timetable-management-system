package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import org.optaplanner.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import java.util.Objects;

/**
 * Nearby distance meter for Rooms.
 * 
 * Measures "distance" between rooms to enable nearby selection optimization.
 * Rooms in the same zone are considered "closer" than rooms in different zones.
 * 
 * Distance calculation:
 * - Same room: 0
 * - Same zone: small distance (1-10 based on capacity difference)
 * - Different zone: larger distance (100+)
 */
public class RoomNearbyDistanceMeter implements NearbyDistanceMeter<Lesson, Room> {

    @Override
    public double getNearbyDistance(Lesson origin, Room destination) {
        Room originRoom = origin.getRoom();
        
        // If origin has no room, all destinations are equally far
        if (originRoom == null) {
            return Double.MAX_VALUE;
        }
        
        // Same room = distance 0
        if (originRoom.equals(destination)) {
            return 0.0;
        }
        
        // Check if same zone
        boolean sameZone = originRoom.getZone() != null 
            && destination.getZone() != null
            && Objects.equals(originRoom.getZone().getId(), destination.getZone().getId());
        
        // Capacity difference (normalized)
        int capacityDiff = Math.abs(originRoom.getCapacity() - destination.getCapacity());
        
        if (sameZone) {
            // Same zone: small distance based on capacity similarity
            return 1.0 + (capacityDiff / 10.0);
        } else {
            // Different zone: much larger distance
            return 100.0 + (capacityDiff / 10.0);
        }
    }
}
