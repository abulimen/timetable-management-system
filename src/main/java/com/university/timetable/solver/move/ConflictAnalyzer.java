package com.university.timetable.solver.move;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.TimeTable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes the current timetable solution to find lessons involved in
 * hard constraint violations and builds conflict clusters for
 * ruin-and-recreate.
 * <p>
 * Uses fast O(n) checks for common conflicts (same timeslot+room, same
 * timeslot+lecturer) rather than full constraint stream evaluation.
 */
public class ConflictAnalyzer {

    /**
     * Find all non-pinned lessons involved in hard constraint violations.
     * Returns them sorted by number of violations (most-blamed first).
     */
    public static List<Lesson> findConflictingLessons(TimeTable solution) {
        List<Lesson> lessons = solution.getLessons();
        if (lessons == null || lessons.isEmpty()) {
            return List.of();
        }

        // Count violations per lesson
        Map<Long, Integer> violationCount = new HashMap<>();

        // Check all pairs for conflicts
        for (int i = 0; i < lessons.size(); i++) {
            Lesson a = lessons.get(i);
            if (a.isPinned() || a.getTimeslot() == null)
                continue;

            for (int j = i + 1; j < lessons.size(); j++) {
                Lesson b = lessons.get(j);
                if (b.isPinned() || b.getTimeslot() == null)
                    continue;

                // Only check lessons in the same timeslot (conflicts require same time)
                if (!Objects.equals(a.getTimeslot(), b.getTimeslot())) {
                    continue;
                }

                boolean conflict = false;

                // Room conflict: same timeslot + same room
                if (a.getRoom() != null && Objects.equals(a.getRoom(), b.getRoom())) {
                    conflict = true;
                }

                // Lecturer conflict: same timeslot + same lecturer
                if (a.getLecturer() != null && b.getLecturer() != null
                        && Objects.equals(a.getLecturer().getId(), b.getLecturer().getId())) {
                    conflict = true;
                }

                // Student group conflict: same timeslot + overlapping groups
                if (hasGroupOverlap(a, b)) {
                    conflict = true;
                }

                if (conflict) {
                    violationCount.merge(a.getId(), 1, Integer::sum);
                    violationCount.merge(b.getId(), 1, Integer::sum);
                }
            }
        }

        if (violationCount.isEmpty()) {
            return List.of();
        }

        // Sort by violation count descending
        Map<Long, Lesson> lessonById = lessons.stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.toMap(Lesson::getId, l -> l, (a, b) -> a));

        return violationCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(entry -> lessonById.get(entry.getKey()))
                .filter(Objects::nonNull)
                .filter(l -> !l.isPinned())
                .toList();
    }

    /**
     * Build a conflict cluster starting from a seed lesson.
     * Follows the conflict chain: seed → direct conflict partners → their partners
     * (1 level).
     *
     * @param seed           the most-blamed lesson
     * @param allLessons     all lessons in the solution
     * @param maxClusterSize maximum cluster size (from admin settings)
     * @return ordered list of lessons to ruin, starting with the seed
     */
    public static List<Lesson> buildConflictCluster(Lesson seed, List<Lesson> allLessons, int maxClusterSize) {
        Set<Long> clusterIds = new LinkedHashSet<>();
        clusterIds.add(seed.getId());

        // Level 1: direct conflict partners of the seed
        List<Lesson> directPartners = findDirectConflictPartners(seed, allLessons);
        for (Lesson partner : directPartners) {
            if (clusterIds.size() >= maxClusterSize)
                break;
            if (!partner.isPinned()) {
                clusterIds.add(partner.getId());
            }
        }

        // Level 2: conflict partners of the direct partners
        List<Lesson> level2Candidates = new ArrayList<>();
        for (Lesson partner : directPartners) {
            if (clusterIds.size() >= maxClusterSize)
                break;
            level2Candidates.addAll(findDirectConflictPartners(partner, allLessons));
        }
        for (Lesson candidate : level2Candidates) {
            if (clusterIds.size() >= maxClusterSize)
                break;
            if (!candidate.isPinned()) {
                clusterIds.add(candidate.getId());
            }
        }

        // Convert IDs back to lessons
        Map<Long, Lesson> lessonById = allLessons.stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.toMap(Lesson::getId, l -> l, (a, b) -> a));

        return clusterIds.stream()
                .map(lessonById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Find lessons that directly conflict with the given lesson
     * (same timeslot + same room/lecturer/group).
     */
    private static List<Lesson> findDirectConflictPartners(Lesson target, List<Lesson> allLessons) {
        if (target.getTimeslot() == null)
            return List.of();

        List<Lesson> partners = new ArrayList<>();
        for (Lesson other : allLessons) {
            if (other.equals(target) || other.isPinned() || other.getTimeslot() == null)
                continue;
            if (!Objects.equals(target.getTimeslot(), other.getTimeslot()))
                continue;

            boolean conflict = false;

            if (target.getRoom() != null && Objects.equals(target.getRoom(), other.getRoom())) {
                conflict = true;
            }
            if (target.getLecturer() != null && other.getLecturer() != null
                    && Objects.equals(target.getLecturer().getId(), other.getLecturer().getId())) {
                conflict = true;
            }
            if (hasGroupOverlap(target, other)) {
                conflict = true;
            }

            if (conflict) {
                partners.add(other);
            }
        }
        return partners;
    }

    /**
     * Check if two lessons have overlapping student groups.
     */
    private static boolean hasGroupOverlap(Lesson a, Lesson b) {
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
}
