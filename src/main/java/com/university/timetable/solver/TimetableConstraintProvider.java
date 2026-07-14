package com.university.timetable.solver;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.SpecialEvent;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.service.ConstraintSettingsService;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * TimetableConstraintProvider - defines all constraints for the solver.
 * 
 * PERFORMANCE OPTIMIZED: Settings are cached on first access to avoid
 * repeated lookups during constraint evaluation.
 */
public class TimetableConstraintProvider implements ConstraintProvider {
    private static final Logger log = LoggerFactory.getLogger(TimetableConstraintProvider.class);

    // Cached settings - loaded once on first access
    private volatile boolean settingsInitialized = false;
    private volatile boolean loadedFromDb = false; // Track if we actually loaded from DB
    private LocalTime cachedLunchBreakStart;
    private LocalTime cachedLunchBreakEnd;
    private LocalTime cachedLatestEndTime;
    private LocalTime cachedFridayLatestEndTime;
    private boolean cachedLunchBreakEnforced;
    private boolean cachedSameCourseSameDayAllowed;
    private boolean cachedDayBalanceEnforced;
    private int cachedWeightRoomCapacity;
    private int cachedWeightDayBalance;
    private int cachedWeightLecturerTransition;
    private int cachedWeightStudentFatigue;
    private int cachedWeightEarlyMorning;
    private int cachedWeightLecturerFatigue;
    private int cachedWeightLateAfternoon;
    private int cachedMaxLecturerConsecutiveHours;
    private boolean cachedUnavailabilitySystemEnabled;

    /**
     * Initialize all settings from the service.
     * If the Spring context wasn't ready on first call, we retry on subsequent
     * calls.
     */
    private void initializeSettings() {
        // If we already loaded from DB, don't retry
        if (loadedFromDb) {
            return;
        }

        synchronized (this) {
            // Double-check inside synchronized block
            if (loadedFromDb) {
                return;
            }

            ConstraintSettingsService svc = SpringContextHolder.getBean(ConstraintSettingsService.class);
            if (svc != null) {
                log.info("Loading solver constraint settings from DB cache...");
                cachedLunchBreakStart = svc.getLunchBreakStart();
                cachedLunchBreakEnd = svc.getLunchBreakEnd();
                cachedLatestEndTime = svc.getLatestEndTime();
                cachedFridayLatestEndTime = svc.getFridayLatestEndTime();
                cachedLunchBreakEnforced = svc.isLunchBreakEnforced();
                cachedSameCourseSameDayAllowed = svc.isSameCourseSameDayAllowed();
                cachedDayBalanceEnforced = svc.isDayBalanceEnforced();
                cachedUnavailabilitySystemEnabled = svc.isUnavailabilitySystemEnabled();
                cachedWeightRoomCapacity = svc.getWeightRoomCapacity();
                cachedWeightDayBalance = svc.getWeightDayBalance();
                cachedWeightLecturerTransition = svc.getWeightLecturerTransition();
                cachedWeightStudentFatigue = svc.getWeightStudentFatigue();
                cachedWeightEarlyMorning = svc.getInt("weight_early_morning", 3);
                cachedWeightLecturerFatigue = svc.getInt("weight_lecturer_fatigue", 1);
                cachedWeightLateAfternoon = svc.getInt("weight_late_afternoon", 3);
                cachedMaxLecturerConsecutiveHours = svc.getMaxLecturerConsecutiveHours();
                loadedFromDb = true;
                log.info(
                        "Constraint settings loaded: lunchBreakEnd={}, latestEndTime={}, fridayEndTime={}, unavailabilityEnabled={}",
                        cachedLunchBreakEnd, cachedLatestEndTime, cachedFridayLatestEndTime,
                        cachedUnavailabilitySystemEnabled);
            } else if (!settingsInitialized) {
                // Only set defaults if we haven't set anything yet
                log.warn("ConstraintSettingsService unavailable; using defaults until service is ready.");
                cachedLunchBreakStart = LocalTime.of(12, 0);
                cachedLunchBreakEnd = LocalTime.of(14, 0);
                cachedLatestEndTime = LocalTime.of(18, 0);
                cachedFridayLatestEndTime = LocalTime.of(12, 0);
                cachedLunchBreakEnforced = true;
                cachedSameCourseSameDayAllowed = false;
                cachedDayBalanceEnforced = true;
                cachedUnavailabilitySystemEnabled = false;
                cachedWeightRoomCapacity = 1;
                cachedWeightDayBalance = 2;
                cachedWeightLecturerTransition = 5;
                cachedWeightStudentFatigue = 1;
                cachedWeightEarlyMorning = 3;
                cachedWeightLecturerFatigue = 1;
                cachedWeightLateAfternoon = 3;
                cachedMaxLecturerConsecutiveHours = 4;
                settingsInitialized = true;
            }
        }
    }

    // Fast cached getters — no service lookup needed; settings loaded once in
    // defineConstraints()
    private LocalTime getLunchBreakStart() {
        return cachedLunchBreakStart;
    }

    private LocalTime getLunchBreakEnd() {
        return cachedLunchBreakEnd;
    }

    private boolean isLunchBreakEnforced() {
        return cachedLunchBreakEnforced;
    }

    private boolean isSameCourseSameDayAllowed() {
        return cachedSameCourseSameDayAllowed;
    }

    private boolean isDayBalanceEnforced() {
        return cachedDayBalanceEnforced;
    }

    private int getWeightRoomCapacity() {
        return cachedWeightRoomCapacity;
    }

    private int getWeightDayBalance() {
        return cachedWeightDayBalance;
    }

    private int getWeightLecturerTransition() {
        return cachedWeightLecturerTransition;
    }

    private int getWeightStudentFatigue() {
        return cachedWeightStudentFatigue;
    }

    private int getWeightEarlyMorning() {
        return cachedWeightEarlyMorning;
    }

    private LocalTime getLatestEndTime() {
        return cachedLatestEndTime;
    }

    private LocalTime getFridayLatestEndTime() {
        return cachedFridayLatestEndTime;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        // Consolidate: load all settings once per solve, not per constraint evaluation
        initializeSettings();

        return new Constraint[] {
                // Hard Constraints
                roomConflict(factory),
                lecturerConflict(factory),
                studentGroupConflict(factory),
                roomFeatureRequired(factory),
                zoneRestriction(factory),
                lecturerUnavailability(factory),
                lunchBreakOverlap(factory),
                sameCourseOnSameDay(factory),
                lessonExceedsEndTime(factory),
                roomCapacityOverflow(factory),
                specialEventConflict(factory),
                maxLecturerConsecutiveHoursConstraint(factory),

                // Soft Constraints
                roomCapacityEfficiency(factory),
                studentFatigue(factory),
                lecturerRoomTransition(factory),
                dayBalanceForStudentGroup(factory),
                earlyMorningPenalty(factory),
                lateAfternoonPenalty(factory),
                lecturerFatigue(factory)
        };
    }

    // ==================== HARD CONSTRAINTS ====================

    private Constraint roomConflict(ConstraintFactory factory) {
        // Important for CH performance: avoid joining lessons with null room.
        // During construction, many lessons have timeslot assigned before room;
        // joining on room=null creates a massive useless pair explosion.
        var scheduledRoomLessons = factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null
                        && lesson.getRoom() != null
                        && !lesson.isOnline());
        return scheduledRoomLessons
                .join(scheduledRoomLessons,
                        Joiners.lessThan(Lesson::getId, Lesson::getId),
                        Joiners.equal(Lesson::getRoom),
                        Joiners.equal(this::lessonDay),
                        Joiners.overlapping(this::lessonStart, Lesson::getEndTime, this::lessonStart,
                                Lesson::getEndTime))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    private Constraint lecturerConflict(ConstraintFactory factory) {
        // Pre-filter lecturer!=null before join to avoid null-equality fan-out.
        var scheduledLecturerLessons = factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getLecturer() != null);
        return scheduledLecturerLessons
                .join(scheduledLecturerLessons,
                        Joiners.lessThan(Lesson::getId, Lesson::getId),
                        Joiners.equal(Lesson::getLecturer),
                        Joiners.equal(this::lessonDay),
                        Joiners.overlapping(this::lessonStart, Lesson::getEndTime, this::lessonStart,
                                Lesson::getEndTime))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lecturer conflict");
    }

    private Constraint studentGroupConflict(ConstraintFactory factory) {
        var scheduledLessonsWithGroups = factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null && !lesson.getConflictGroupIds().isEmpty());
        return scheduledLessonsWithGroups
                .join(scheduledLessonsWithGroups,
                        Joiners.lessThan(Lesson::getId, Lesson::getId),
                        Joiners.equal(this::lessonDay),
                        Joiners.overlapping(this::lessonStart, Lesson::getEndTime, this::lessonStart,
                                Lesson::getEndTime))
                .filter(this::hasStudentGroupOverlap)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Student group conflict");
    }

    /**
     * Check if two lessons have overlapping student groups.
     * Handles both single and combined (multiple) groups.
     */
    private boolean hasStudentGroupOverlap(Lesson l1, Lesson l2) {
        Set<Long> conflictIds1 = l1.getConflictGroupIds();
        Set<Long> conflictIds2 = l2.getConflictGroupIds();
        if (conflictIds1.isEmpty() || conflictIds2.isEmpty()) {
            return false;
        }
        Set<Long> smaller = conflictIds1.size() <= conflictIds2.size() ? conflictIds1 : conflictIds2;
        Set<Long> larger = smaller == conflictIds1 ? conflictIds2 : conflictIds1;
        for (Long id : smaller) {
            if (larger.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private Constraint roomFeatureRequired(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> !lesson.isOnline() && // Skip online lessons
                        lesson.getRoom() != null &&
                        lesson.getCourse() != null &&
                        lesson.getCourse().getRequiredFeatures() != null &&
                        !lesson.getCourse().getRequiredFeatures().isEmpty() &&
                        !lesson.getRoom().hasAllFeatures(lesson.getCourse().getRequiredFeatures()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room feature required");
    }

    private Constraint zoneRestriction(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> !lesson.isOnline() && // Skip online lessons
                        lesson.getRoom() != null &&
                        lesson.getCourse() != null &&
                        lesson.getCourse().getAllowedZones() != null &&
                        !lesson.getCourse().getAllowedZones().isEmpty() &&
                        lesson.getRoom().getZone() != null &&
                        !lesson.getCourse().getAllowedZones().contains(lesson.getRoom().getZone()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Zone restriction");
    }

    /**
     * Lecturer Unavailability - CONFIGURABLE.
     * Only applies if the unavailability system is enabled in settings.
     */
    private Constraint lecturerUnavailability(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> isUnavailabilitySystemEnabled() &&
                        lesson.getLecturer() != null &&
                        lesson.getTimeslot() != null &&
                        !lesson.getLecturer().isAvailableAt(lesson.getTimeslot(), lesson.getDurationHours()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lecturer unavailability");
    }

    private boolean isUnavailabilitySystemEnabled() {
        return cachedUnavailabilitySystemEnabled;
    }

    /**
     * CONFIGURABLE: Lunch Break Overlap
     * Lessons cannot overlap the configured lunch break period.
     */
    private Constraint lunchBreakOverlap(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> {
                    if (!isLunchBreakEnforced()) {
                        return false;
                    }
                    if (lesson.getTimeslot() == null) {
                        return false;
                    }

                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lesson.getEndTime();
                    LocalTime lunchStart = getLunchBreakStart();
                    LocalTime lunchEnd = getLunchBreakEnd();

                    return lessonStart.isBefore(lunchEnd) && lunchStart.isBefore(lessonEnd);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lunch break overlap");
    }

    /**
     * CONFIGURABLE: Same Course on Same Day
     * Lessons of the same course FOR THE SAME GROUP must be on different days.
     * Different groups can have the same course on the same day.
     */
    private Constraint sameCourseOnSameDay(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> !isSameCourseSameDayAllowed() &&
                        lesson.getCourse() != null &&
                        lesson.getTimeslot() != null &&
                        lesson.getStudentGroups() != null &&
                        !lesson.getStudentGroups().isEmpty())
                // Flatten to (course, group, day) tuples
                .flattenLast(lesson -> {
                    Set<LessonGroupDay> tuples = new HashSet<>();
                    DayOfWeek day = lessonDay(lesson);
                    for (StudentGroup group : lesson.getStudentGroups()) {
                        tuples.add(new LessonGroupDay(lesson.getCourse(), group, day));
                    }
                    return tuples;
                })
                .groupBy(LessonGroupDay::course, LessonGroupDay::group, LessonGroupDay::day, ConstraintCollectors.count())
                .filter((course, group, day, count) -> count > 1)
                .penalize(HardSoftScore.ONE_HARD, (course, group, day, count) -> pairCount(count))
                .asConstraint("Same course on same day for same group");
    }
    
    // Helper record for grouping lessons by course+group+day
    private record LessonGroupDay(Course course, StudentGroup group, DayOfWeek day) {}

    /**
     * HARD CONSTRAINT: Lesson Exceeds End Time
     * Lessons must end by the configured latest end time.
     * Friday has a separate configurable end time (default 12:00).
     * Other days use the standard latest end time (default 18:00).
     */
    private Constraint lessonExceedsEndTime(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> {
                    if (lesson.getTimeslot() == null) {
                        return false;
                    }
                    LocalTime lessonEnd = lesson.getEndTime();
                    DayOfWeek day = lesson.getTimeslot().getDayOfWeek();

                    // Friday has its own end time limit
                    LocalTime latestEnd = (day == DayOfWeek.FRIDAY)
                            ? getFridayLatestEndTime()
                            : getLatestEndTime();

                    return lessonEnd.isAfter(latestEnd);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lesson exceeds end time");
    }

    /**
     * HARD CONSTRAINT: Room Capacity Overflow
     * Room must have enough capacity for ALL students in the lesson.
     * For combined classes, this sums all student groups.
     */
    private Constraint roomCapacityOverflow(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> !lesson.isOnline() && // Skip online lessons - no capacity limit
                        lesson.getRoom() != null &&
                        lesson.getTotalStudentCount() > lesson.getRoom().getCapacity())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room capacity overflow");
    }

    /**
     * Special Event Conflict - HARD CONSTRAINT
     * Prevents scheduling lessons for student groups during special events.
     * Also prevents using rooms and lecturers assigned to special events.
     */
    private Constraint specialEventConflict(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .join(SpecialEvent.class,
                        Joiners.equal(
                                lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null,
                                SpecialEvent::getDayOfWeek))
                .filter((lesson, event) -> {
                    if (lesson.getTimeslot() == null || !event.isActive()) {
                        return false;
                    }
                    // Check overlap against the full lesson interval (not just the 1-hour start
                    // slot).
                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lesson.getEndTime();
                    LocalTime eventStart = event.getStartTime();
                    LocalTime eventEnd = event.getEndTime();
                    boolean overlaps = lessonStart.isBefore(eventEnd) && eventStart.isBefore(lessonEnd);
                    if (!overlaps) {
                        return false;
                    }
                    // Check if any of the lesson's student groups are affected
                    for (StudentGroup lessonGroup : lesson.getStudentGroups()) {
                        if (event.affectsStudentGroup(lessonGroup)) {
                            return true;
                        }
                    }
                    // Also check room conflict (if event has a room)
                    if (event.getRoom() != null && lesson.getRoom() != null &&
                            event.getRoom().getId().equals(lesson.getRoom().getId())) {
                        return true;
                    }
                    // Also check lecturer conflict (if event has a lecturer)
                    if (event.getLecturer() != null && lesson.getLecturer() != null &&
                            event.getLecturer().getId().equals(lesson.getLecturer().getId())) {
                        return true;
                    }
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Special event conflict");
    }

    // ==================== SOFT CONSTRAINTS ====================

    private Constraint roomCapacityEfficiency(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getRoom() != null &&
                        lesson.getTotalStudentCount() > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        lesson -> {
                            // Penalize rooms that are too large (wasted capacity)
                            int diff = lesson.getRoom().getCapacity() - lesson.getTotalStudentCount();
                            int weight = getWeightRoomCapacity();
                            // Use ceiling division so even 1 wasted seat incurs penalty
                            return diff > 0 ? (int) Math.ceil(diff / 10.0) * weight : 0;
                        })
                .asConstraint("Room capacity efficiency");
    }

    /**
     * SOFT: Student fatigue — penalizes consecutive teaching chains.
     * Instead of penalizing each pair independently, counts the total
     * number of consecutive lesson pairs per student group per day.
     * This makes longer chains penalized more heavily (e.g., 4 consecutive
     * hours = 3 pairs = 3× penalty, encouraging the solver to break up
     * long blocks rather than just shortening them by one).
     */
    private Constraint studentFatigue(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getStudentGroup() != null)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(this::lessonDay, this::lessonDay),
                        Joiners.equal(Lesson::getEndTime, this::lessonStart))
                .filter((earlier, later) -> !crossesLunchBoundary(earlier, later))
                .penalize(HardSoftScore.ofSoft(getWeightStudentFatigue()))
                .asConstraint("Student fatigue");
    }

    private Constraint lecturerRoomTransition(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null &&
                        lesson.getLecturer() != null &&
                        lesson.getRoom() != null)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getLecturer),
                        Joiners.equal(this::lessonDay, this::lessonDay),
                        Joiners.equal(Lesson::getEndTime, this::lessonStart))
                .filter((earlier, later) -> later.getRoom() != null &&
                        !crossesLunchBoundary(earlier, later) &&
                        !Objects.equals(earlier.getRoom().getId(), later.getRoom().getId()))
                .penalize(HardSoftScore.ofSoft(getWeightLecturerTransition()),
                        (earlier, later) -> {
                            if (earlier.getRoom().getZone() != null &&
                                    later.getRoom().getZone() != null &&
                                    !Objects.equals(earlier.getRoom().getZone().getId(),
                                            later.getRoom().getZone().getId())) {
                                return 3;
                            }
                            return 1;
                        })
                .asConstraint("Lecturer room transition");
    }

    /**
     * SOFT: Day balance for student groups.
     * Properly handles combined classes: each lesson is counted toward ALL
     * participating student groups. For example, a combined "English ADE"
     * lesson counts toward groups A, D, and E independently.
     */
    private Constraint dayBalanceForStudentGroup(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> isDayBalanceEnforced() &&
                        lesson.getTimeslot() != null &&
                        lesson.getStudentGroups() != null &&
                        !lesson.getStudentGroups().isEmpty())
                .flattenLast(lesson -> {
                    Set<GroupDay> tuples = new HashSet<>();
                    DayOfWeek day = lessonDay(lesson);
                    for (StudentGroup group : lesson.getStudentGroups()) {
                        tuples.add(new GroupDay(group.getId(), day));
                    }
                    return tuples;
                })
                .groupBy(GroupDay::groupId, GroupDay::day, ConstraintCollectors.count())
                .filter((groupId, day, lessonCount) -> lessonCount > 1)
                .penalize(HardSoftScore.ofSoft(getWeightDayBalance()),
                        (groupId, day, lessonCount) -> pairCount(lessonCount))
                .asConstraint("Day balance for student group");
    }

    // Helper record for day balance grouping by (studentGroupId, day)
    private record GroupDay(Long groupId, DayOfWeek day) {}

    /**
     * SOFT CONSTRAINT: Graduated Early Morning Penalty
     * Penalizes early morning lessons with decreasing severity:
     * - 7:00 AM → 3x weight (strongest: students really dislike this)
     * - 8:00 AM → 1x weight (mild: slightly discouraged)
     * - 9:00 AM+ → no penalty
     * This distributes lessons away from very early slots while still
     * allowing 8am lessons when needed.
     */
    private Constraint earlyMorningPenalty(ConstraintFactory factory) {
        int weight = getWeightEarlyMorning();
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null &&
                        lesson.getTimeslot().getStartTime().isBefore(LocalTime.of(9, 0)))
                .penalize(HardSoftScore.ONE_SOFT,
                        lesson -> {
                            LocalTime start = lesson.getTimeslot().getStartTime();
                            if (start.equals(LocalTime.of(7, 0))) {
                                return weight * 3; // Heavy penalty for 7am
                            } else { // 8:00 AM
                                return weight; // Mild penalty for 8am
                            }
                        })
                .asConstraint("Early morning penalty");
    }

    /**
     * SOFT CONSTRAINT: Graduated Late Afternoon Penalty
     * Penalizes late afternoon/evening lessons with increasing severity:
     * - 5:00 PM → 1× weight (mild: slightly discouraged)
     * - 6:00 PM+ → 3× weight (strong: students really dislike this)
     * Mirrors the early morning penalty to encourage a balanced daily schedule.
     */
    private Constraint lateAfternoonPenalty(ConstraintFactory factory) {
        int weight = cachedWeightLateAfternoon;
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null &&
                        !lesson.getTimeslot().getStartTime().isBefore(LocalTime.of(17, 0)))
                .penalize(HardSoftScore.ONE_SOFT,
                        lesson -> {
                            LocalTime start = lesson.getTimeslot().getStartTime();
                            if (start.isBefore(LocalTime.of(18, 0))) {
                                return weight; // Mild penalty for 5pm
                            } else {
                                return weight * 3; // Heavy penalty for 6pm+
                            }
                        })
                .asConstraint("Late afternoon penalty");
    }

    /**
     * SOFT: Penalize when lecturers have consecutive teaching hours.
     * Similar to student fatigue but for lecturers.
     */
    private Constraint lecturerFatigue(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getLecturer() != null)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getLecturer),
                        Joiners.equal(this::lessonDay, this::lessonDay),
                        Joiners.equal(Lesson::getEndTime, this::lessonStart))
                .filter((earlier, later) -> !crossesLunchBoundary(earlier, later))
                .penalize(HardSoftScore.ofSoft(cachedWeightLecturerFatigue))
                .asConstraint("Lecturer fatigue");
    }

    private int getMaxLecturerConsecutiveHours() {
        return cachedMaxLecturerConsecutiveHours;
    }

    /**
     * HARD CONSTRAINT: Maximum Lecturer Consecutive Hours
     * Penalizes (hard) when a lecturer teaches more consecutive hours than allowed.
     * Uses a chain-counting approach: counts pairs of back-to-back lessons per
     * lecturer per day. If the number of consecutive pairs exceeds the limit,
     * each excess pair incurs a hard penalty.
     *
     * Example: If limit is 4 hours and a lecturer has 5 consecutive 1-hour lessons,
     * that's 4 consecutive pairs, which exceeds the limit of 3 pairs (limit-1),
     * so 1 hard penalty is applied.
     */
    private Constraint maxLecturerConsecutiveHoursConstraint(ConstraintFactory factory) {
        int maxHours = getMaxLecturerConsecutiveHours();
        if (maxHours <= 0) {
            return factory.forEach(Lesson.class)
                    .filter(l -> false)
                    .penalize(HardSoftScore.ONE_HARD)
                    .asConstraint("Max lecturer consecutive hours");
        }
        // Consecutive hours limit K means at most (K-1) back-to-back pairs
        // in a single daily chain. Penalize only the excess pairs.
        int allowedConsecutivePairs = Math.max(0, maxHours - 1);
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getLecturer() != null)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getLecturer),
                        Joiners.equal(this::lessonDay, this::lessonDay),
                        Joiners.equal(Lesson::getEndTime, this::lessonStart))
                .filter((a, b) -> !crossesLunchBoundary(a, b))
                .groupBy((a, b) -> a.getLecturer(), (a, b) -> lessonDay(a), ConstraintCollectors.countBi())
                .filter((lecturer, day, consecutivePairCount) -> consecutivePairCount > allowedConsecutivePairs)
                .penalize(HardSoftScore.ONE_HARD,
                        (lecturer, day, consecutivePairCount) -> consecutivePairCount - allowedConsecutivePairs)
                .asConstraint("Max lecturer consecutive hours");
    }

    // ==================== HELPER METHODS ====================

    private boolean isScheduledLesson(Lesson lesson) {
        return lesson.getTimeslot() != null;
    }

    private DayOfWeek lessonDay(Lesson lesson) {
        return lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null;
    }

    private LocalTime lessonStart(Lesson lesson) {
        return lesson.getTimeslot() != null ? lesson.getTimeslot().getStartTime() : null;
    }

    private int pairCount(int lessonCount) {
        return (lessonCount * (lessonCount - 1)) / 2;
    }

    private boolean crossesLunchBoundary(Lesson earlier, Lesson later) {
        if (!isLunchBreakEnforced()) {
            return false;
        }
        LocalTime lunchStart = getLunchBreakStart();
        LocalTime lunchEnd = getLunchBreakEnd();
        return Objects.equals(earlier.getEndTime(), lunchStart) &&
                Objects.equals(lessonStart(later), lunchEnd);
    }

}
