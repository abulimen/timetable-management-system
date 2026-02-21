package com.university.timetable.solver.move;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.*;

import java.util.*;

/**
 * Custom move that "ruins" (unassigns) a conflict cluster of lessons,
 * then "recreates" (reassigns) them using a greedy best-fit strategy.
 * <p>
 * This is the core ruin-and-recreate move. It only targets lessons that are
 * involved in hard constraint violations, rebuilding the tangled section
 * from scratch.
 */
public class RuinAndRecreateMove implements Move<TimeTable> {

    private final List<Lesson> cluster;
    private final List<Timeslot> allTimeslots;
    private final List<Room> allRooms;

    public RuinAndRecreateMove(List<Lesson> cluster, List<Timeslot> allTimeslots, List<Room> allRooms) {
        this.cluster = cluster;
        this.allTimeslots = allTimeslots;
        this.allRooms = allRooms;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TimeTable> scoreDirector) {
        // Must have at least 2 lessons to ruin (otherwise a normal move suffices)
        if (cluster.size() < 2)
            return false;
        // At least one lesson must be assigned (otherwise nothing to ruin)
        return cluster.stream().anyMatch(l -> l.getTimeslot() != null || l.getRoom() != null);
    }

    @Override
    public void doMoveOnly(ScoreDirector<TimeTable> scoreDirector) {
        // Phase 1: RUIN — Save current assignments and unassign all lessons in the
        // cluster
        Map<Lesson, Timeslot> savedTimeslots = new HashMap<>();
        Map<Lesson, Room> savedRooms = new HashMap<>();

        for (Lesson lesson : cluster) {
            savedTimeslots.put(lesson, lesson.getTimeslot());
            savedRooms.put(lesson, lesson.getRoom());

            scoreDirector.beforeVariableChanged(lesson, "timeslot");
            lesson.setTimeslot(null);
            scoreDirector.afterVariableChanged(lesson, "timeslot");

            scoreDirector.beforeVariableChanged(lesson, "room");
            lesson.setRoom(null);
            scoreDirector.afterVariableChanged(lesson, "room");
        }

        // Phase 2: RECREATE — Greedy reconstruction by difficulty (hardest lessons
        // first)
        // Sort by total student count descending (harder lessons get assigned first)
        List<Lesson> sortedCluster = new ArrayList<>(cluster);
        sortedCluster.sort(Comparator.comparingInt(Lesson::getTotalStudentCount).reversed());

        for (Lesson lesson : sortedCluster) {
            Timeslot bestTimeslot = null;
            Room bestRoom = null;
            int bestScore = Integer.MIN_VALUE;

            // Try each compatible timeslot+room combination, pick the best
            for (Timeslot ts : allTimeslots) {
                for (Room room : allRooms) {
                    // Pre-filter: skip incompatible rooms
                    if (!NearbyMoveFactory.isRoomCompatible(lesson, room)) {
                        continue;
                    }

                    // Score this assignment: prefer fewer conflicts with already-assigned lessons
                    int score = scoreAssignment(lesson, ts, room, sortedCluster);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTimeslot = ts;
                        bestRoom = room;
                    }
                }
            }

            // Assign the best found (or fallback to saved if nothing works)
            Timeslot assignTs = bestTimeslot != null ? bestTimeslot : savedTimeslots.get(lesson);
            Room assignRoom = bestRoom != null ? bestRoom : savedRooms.get(lesson);

            scoreDirector.beforeVariableChanged(lesson, "timeslot");
            lesson.setTimeslot(assignTs);
            scoreDirector.afterVariableChanged(lesson, "timeslot");

            scoreDirector.beforeVariableChanged(lesson, "room");
            lesson.setRoom(assignRoom);
            scoreDirector.afterVariableChanged(lesson, "room");
        }
    }

    /**
     * Score a potential assignment. Higher = better.
     * Checks for conflicts with already-assigned lessons in the cluster.
     */
    private int scoreAssignment(Lesson lesson, Timeslot timeslot, Room room,
            List<Lesson> allClusterLessons) {
        int score = 0;

        for (Lesson other : allClusterLessons) {
            if (other.equals(lesson) || other.getTimeslot() == null)
                continue;

            // Same timeslot: check for conflicts
            if (Objects.equals(other.getTimeslot(), timeslot)) {
                // Room conflict
                if (Objects.equals(other.getRoom(), room)) {
                    score -= 100;
                }
                // Lecturer conflict
                if (lesson.getLecturer() != null && other.getLecturer() != null
                        && Objects.equals(lesson.getLecturer().getId(), other.getLecturer().getId())) {
                    score -= 100;
                }
                // Student group conflict
                if (hasGroupOverlap(lesson, other)) {
                    score -= 100;
                }
            }
        }

        // Slight preference for rooms that fit well (not too big, not too small)
        int excessCapacity = room.getCapacity() - lesson.getTotalStudentCount();
        if (excessCapacity >= 0) {
            score -= excessCapacity; // Penalize waste (smaller penalty than conflicts)
        }

        return score;
    }

    private boolean hasGroupOverlap(Lesson a, Lesson b) {
        Set<Long> aIds = a.getConflictGroupIds();
        Set<Long> bIds = b.getConflictGroupIds();
        if (aIds.isEmpty() || bIds.isEmpty())
            return false;
        for (Long id : aIds) {
            if (bIds.contains(id))
                return true;
        }
        return false;
    }

    @Override
    public RuinAndRecreateMove rebase(ScoreDirector<TimeTable> destinationScoreDirector) {
        List<Lesson> rebasedCluster = cluster.stream()
                .map(destinationScoreDirector::lookUpWorkingObject)
                .toList();
        List<Timeslot> rebasedTimeslots = allTimeslots.stream()
                .map(destinationScoreDirector::lookUpWorkingObject)
                .toList();
        List<Room> rebasedRooms = allRooms.stream()
                .map(destinationScoreDirector::lookUpWorkingObject)
                .toList();
        return new RuinAndRecreateMove(rebasedCluster, rebasedTimeslots, rebasedRooms);
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return cluster;
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of();
    }

    public List<Lesson> getCluster() {
        return cluster;
    }

    @Override
    public String toString() {
        return "RuinAndRecreate(cluster=" + cluster.size() + " lessons, seeds="
                + cluster.stream().limit(3).map(l -> String.valueOf(l.getId())).toList() + ")";
    }
}
