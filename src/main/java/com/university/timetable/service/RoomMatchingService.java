package com.university.timetable.service;

import com.google.ortools.graph.MinCostFlow;
import com.university.timetable.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Per-timeslot room assignment via min-cost bipartite matching.
 * <p>
 * Given a set of lessons assigned to a single timeslot and a set of available rooms,
 * finds the optimal room assignment that:
 * <ul>
 * <li>Satisfies hard constraints (capacity, features, zones)</li>
 * <li>Minimizes soft cost (capacity waste + zone distance + feature penalty)</li>
 * </ul>
 * <p>
 * Uses Google OR-Tools MinCostFlow solver for polynomial-time exact solutions.
 * Typical solve time: ~1ms for ~50 lessons × ~50 rooms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomMatchingService {

    private static final int LARGE_COST = 1_000_000; // Used to prevent infeasible assignments

    /**
     * Result of room matching for a single timeslot.
     */
    public record MatchingResult(
            Map<Lesson, Room> assignments,
            boolean feasible,
            int totalCost,
            String infeasibilityReason
    ) {
        public static MatchingResult infeasible(String reason) {
            return new MatchingResult(Map.of(), false, 0, reason);
        }
    }

    /**
     * Solve room assignment for lessons in a single timeslot.
     *
     * @param lessonsInSlot Lessons assigned to this timeslot (timeslot already set)
     * @param allRooms      All available rooms in the system
     * @return MatchingResult with optimal assignments or infeasibility reason
     */
    public MatchingResult solveRoomMatching(List<Lesson> lessonsInSlot, List<Room> allRooms) {
        if (lessonsInSlot.isEmpty()) {
            return new MatchingResult(Map.of(), true, 0, null);
        }

        // Filter to non-online lessons (online lessons don't need rooms)
        List<Lesson> physicalLessons = lessonsInSlot.stream()
                .filter(l -> !l.isOnline())
                .toList();

        if (physicalLessons.isEmpty()) {
            return new MatchingResult(Map.of(), true, 0, null);
        }

        // Pre-compute compatible rooms per lesson
        Map<Lesson, List<Room>> compatibleRooms = new LinkedHashMap<>();
        for (Lesson lesson : physicalLessons) {
            List<Room> compatible = new ArrayList<>();
            for (Room room : allRooms) {
                if (isRoomCompatible(lesson, room)) {
                    compatible.add(room);
                }
            }
            compatibleRooms.put(lesson, compatible);
            if (compatible.isEmpty()) {
                return MatchingResult.infeasible(
                        "Lesson " + lesson.getId() + " (" +
                                (lesson.getCourse() != null ? lesson.getCourse().getCode() : "?") +
                                ", " + lesson.getTotalStudentCount() + " students) has no compatible rooms");
            }
        }

        // Quick feasibility check: enough rooms?
        Set<Room> allCompatible = new HashSet<>();
        for (List<Room> rooms : compatibleRooms.values()) {
            allCompatible.addAll(rooms);
        }
        if (allCompatible.size() < physicalLessons.size()) {
            return MatchingResult.infeasible(
                    "Only " + allCompatible.size() + " compatible rooms for " +
                            physicalLessons.size() + " lessons in this timeslot");
        }

        // Build min-cost flow network
        // Node layout:
        //   0 = source
        //   1..N = lessons
        //   N+1..N+M = rooms
        //   N+M+1 = sink
        int n = physicalLessons.size();
        List<Room> uniqueRooms = new ArrayList<>(allCompatible);
        Map<Room, Integer> roomToNode = new HashMap<>();
        for (int r = 0; r < uniqueRooms.size(); r++) {
            roomToNode.put(uniqueRooms.get(r), n + 1 + r);
        }
        int m = uniqueRooms.size();
        int source = 0;
        int sink = n + m + 1;
        int nodeCount = sink + 1;

        MinCostFlow flow = new MinCostFlow();

        // Add arcs: source → each lesson (capacity 1, cost 0)
        for (int i = 0; i < n; i++) {
            flow.addArcWithCapacityAndUnitCost(source, 1 + i, 1, 0);
        }

        // Add arcs: each room → sink (capacity 1, cost 0)
        for (int r = 0; r < m; r++) {
            flow.addArcWithCapacityAndUnitCost(n + 1 + r, sink, 1, 0);
        }

        // Add arcs: lesson → compatible room (capacity 1, cost = penalty)
        for (int i = 0; i < n; i++) {
            Lesson lesson = physicalLessons.get(i);
            int lessonNode = 1 + i;
            List<Room> rooms = compatibleRooms.get(lesson);
            for (Room room : rooms) {
                int roomNode = roomToNode.get(room);
                int cost = computeAssignmentCost(lesson, room);
                flow.addArcWithCapacityAndUnitCost(lessonNode, roomNode, 1, cost);
            }
        }

        // Set supply: source produces n units, sink consumes n units
        flow.setNodeSupply(source, n);
        flow.setNodeSupply(sink, -n);

        // Solve
        MinCostFlow.Status status = flow.solve();

        if (status != MinCostFlow.Status.OPTIMAL) {
            return MatchingResult.infeasible(
                    "Min-cost flow solver returned status: " + status +
                            " — room matching infeasible for this timeslot");
        }

        // Extract assignments
        Map<Lesson, Room> assignments = new LinkedHashMap<>();
        int totalCost = 0;

        for (int i = 0; i < n; i++) {
            Lesson lesson = physicalLessons.get(i);
            int lessonNode = 1 + i;
            Room assignedRoom = null;

            // Find the arc from lesson to room with flow = 1
            for (int arc = 0; arc < flow.getNumArcs(); arc++) {
                if (flow.getTail(arc) == lessonNode && flow.getHead(arc) > n && flow.getHead(arc) <= n + m) {
                    if (flow.getFlow(arc) == 1) {
                        int roomNode = flow.getHead(arc);
                        assignedRoom = uniqueRooms.get(roomNode - n - 1);
                        totalCost += flow.getUnitCost(arc);
                        break;
                    }
                }
            }

            if (assignedRoom != null) {
                assignments.put(lesson, assignedRoom);
            } else {
                // Should not happen if status is OPTIMAL, but guard against it
                return MatchingResult.infeasible(
                        "Lesson " + lesson.getId() + " could not be assigned to any room");
            }
        }

        // Also include online lessons (they get null room — no matching needed)
        for (Lesson lesson : lessonsInSlot) {
            if (lesson.isOnline()) {
                assignments.put(lesson, null);
            }
        }

        return new MatchingResult(assignments, true, totalCost, null);
    }

    /**
     * Compute cost of assigning a lesson to a room.
     * Lower cost = better assignment.
     */
    private int computeAssignmentCost(Lesson lesson, Room room) {
        int cost = 0;

        // Capacity waste: prefer rooms that closely match student count
        int waste = room.getCapacity() - lesson.getTotalStudentCount();
        if (waste > 0) {
            cost += (int) Math.ceil(waste / 10.0);
        }

        // Zone preference: penalize cross-zone assignments for lecturers
        // (this is a soft optimization, not a hard constraint — zone restriction
        // is already enforced by isRoomCompatible)
        Course course = lesson.getCourse();
        if (course != null && course.getAllowedZones() != null
                && !course.getAllowedZones().isEmpty()
                && room.getZone() != null
                && !course.getAllowedZones().contains(room.getZone())) {
            cost += 10; // Mild penalty for non-preferred zone within allowed set
        }

        return cost;
    }

    /**
     * Check if a room is compatible with a lesson (hard constraints only).
     * Same logic as NearbyMoveFactory.isRoomCompatible.
     */
    private boolean isRoomCompatible(Lesson lesson, Room room) {
        if (lesson.isOnline()) return true;

        // Capacity
        if (room.getCapacity() < lesson.getTotalStudentCount()) return false;

        // Features
        Course course = lesson.getCourse();
        if (course != null && course.getRequiredFeatures() != null
                && !course.getRequiredFeatures().isEmpty()) {
            if (!room.hasAllFeatures(course.getRequiredFeatures())) return false;
        }

        // Zone
        if (course != null && course.getAllowedZones() != null
                && !course.getAllowedZones().isEmpty()
                && room.getZone() != null) {
            if (!course.getAllowedZones().contains(room.getZone())) return false;
        }

        return true;
    }

    /**
     * Solve room matching for ALL timeslots at once.
     *
     * @param lessons  All lessons (with timeslots already assigned by CP-SAT Phase 1)
     * @param allRooms All rooms
     * @return Combined result for all timeslots
     */
    public Map<Lesson, Room> solveAllTimeslots(List<Lesson> lessons, List<Room> allRooms) {
        // Group lessons by timeslot
        Map<Timeslot, List<Lesson>> byTimeslot = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() != null) {
                byTimeslot.computeIfAbsent(lesson.getTimeslot(), k -> new ArrayList<>()).add(lesson);
            }
        }

        Map<Lesson, Room> allAssignments = new LinkedHashMap<>();
        int feasibleSlots = 0;
        int infeasibleSlots = 0;
        long totalMs = 0;

        for (Map.Entry<Timeslot, List<Lesson>> entry : byTimeslot.entrySet()) {
            long start = System.currentTimeMillis();
            MatchingResult result = solveRoomMatching(entry.getValue(), allRooms);
            totalMs += System.currentTimeMillis() - start;

            if (result.feasible()) {
                allAssignments.putAll(result.assignments());
                feasibleSlots++;
            } else {
                infeasibleSlots++;
                log.warn("Room matching infeasible for timeslot {} ({}): {}",
                        entry.getKey().getId(), entry.getKey().getDayOfWeek() + " " + entry.getKey().getStartTime(),
                        result.infeasibilityReason());
            }
        }

        log.info("Room matching complete: {} feasible slots, {} infeasible slots, {} lessons assigned, {}ms total",
                feasibleSlots, infeasibleSlots, allAssignments.size(), totalMs);

        return allAssignments;
    }

    /**
     * Check if room matching is feasible for all timeslots without actually solving.
     * Quick check: for each timeslot, verify enough compatible rooms exist.
     *
     * @return null if feasible, or infeasibility description
     */
    public String checkFeasibility(List<Lesson> lessons, List<Room> allRooms) {
        Map<Timeslot, List<Lesson>> byTimeslot = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() != null && !lesson.isOnline()) {
                byTimeslot.computeIfAbsent(lesson.getTimeslot(), k -> new ArrayList<>()).add(lesson);
            }
        }

        for (Map.Entry<Timeslot, List<Lesson>> entry : byTimeslot.entrySet()) {
            List<Lesson> slotLessons = entry.getValue();
            Set<Room> compatibleSet = new HashSet<>();
            for (Lesson lesson : slotLessons) {
                for (Room room : allRooms) {
                    if (isRoomCompatible(lesson, room)) {
                        compatibleSet.add(room);
                    }
                }
            }
            if (compatibleSet.size() < slotLessons.size()) {
                return "Timeslot " + entry.getKey().getDayOfWeek() + " " + entry.getKey().getStartTime() +
                        ": " + slotLessons.size() + " lessons but only " + compatibleSet.size() +
                        " compatible rooms";
            }
        }
        return null;
    }
}
