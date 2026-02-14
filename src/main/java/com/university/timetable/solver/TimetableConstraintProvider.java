package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.SpecialEvent;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.service.ConstraintSettingsService;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

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
                cachedMaxLecturerConsecutiveHours = 4;
                settingsInitialized = true;
            }
        }
    }

    // Fast cached getters - no service lookup after initialization
    private LocalTime getLunchBreakStart() {
        initializeSettings();
        return cachedLunchBreakStart;
    }

    private LocalTime getLunchBreakEnd() {
        initializeSettings();
        return cachedLunchBreakEnd;
    }

    private boolean isLunchBreakEnforced() {
        initializeSettings();
        return cachedLunchBreakEnforced;
    }

    private boolean isSameCourseSameDayAllowed() {
        initializeSettings();
        return cachedSameCourseSameDayAllowed;
    }

    private boolean isDayBalanceEnforced() {
        initializeSettings();
        return cachedDayBalanceEnforced;
    }

    private int getWeightRoomCapacity() {
        initializeSettings();
        return cachedWeightRoomCapacity;
    }

    private int getWeightDayBalance() {
        initializeSettings();
        return cachedWeightDayBalance;
    }

    private int getWeightLecturerTransition() {
        initializeSettings();
        return cachedWeightLecturerTransition;
    }

    private int getWeightStudentFatigue() {
        initializeSettings();
        return cachedWeightStudentFatigue;
    }

    private int getWeightEarlyMorning() {
        initializeSettings();
        return cachedWeightEarlyMorning;
    }

    private LocalTime getLatestEndTime() {
        initializeSettings();
        return cachedLatestEndTime;
    }

    private LocalTime getFridayLatestEndTime() {
        initializeSettings();
        return cachedFridayLatestEndTime;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
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
                roomCapacityOverflow(factory), // Room must fit all students
                specialEventConflict(factory), // Prevent scheduling during special events

                // Soft Constraints
                roomCapacityEfficiency(factory),
                studentFatigue(factory),
                lecturerRoomTransition(factory),
                dayBalanceForStudentGroup(factory),
                earlyMorningPenalty(factory),
                lecturerFatigue(factory)
        };
    }

    // ==================== HARD CONSTRAINTS ====================

    private Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getRoom),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> !lesson1.isOnline() && !lesson2.isOnline() && // Skip online lessons
                        lesson1.getRoom() != null &&
                        lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        intervalsOverlap(lesson1, lesson2))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    private Constraint lecturerConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getLecturer),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> lesson1.getLecturer() != null &&
                        lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        intervalsOverlap(lesson1, lesson2))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lecturer conflict");
    }

    private Constraint studentGroupConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        hasStudentGroupOverlap(lesson1, lesson2) &&
                        intervalsOverlap(lesson1, lesson2))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Student group conflict");
    }

    /**
     * Check if two lessons have overlapping student groups.
     * Handles both single and combined (multiple) groups.
     */
    private boolean hasStudentGroupOverlap(Lesson l1, Lesson l2) {
        for (StudentGroup g1 : l1.getStudentGroups()) {
            for (StudentGroup g2 : l2.getStudentGroups()) {
                if (g1.hasConflictWith(g2)) {
                    return true;
                }
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
        initializeSettings();
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
     */
    private Constraint sameCourseOnSameDay(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getCourse),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> !isSameCourseSameDayAllowed() &&
                        lesson1.getCourse() != null &&
                        lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same course on same day");
    }

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
                .join(SpecialEvent.class)
                .filter((lesson, event) -> {
                    if (lesson.getTimeslot() == null || !event.isActive()) {
                        return false;
                    }
                    // Check if lesson overlaps with special event time
                    if (!event.overlapsWithTimeslot(lesson.getTimeslot())) {
                        return false;
                    }
                    // Check if any of the lesson's student groups are affected
                    for (StudentGroup lessonGroup : lesson.getCourse().getAllStudentGroups()) {
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
                            return Math.max(0, (diff / 10) * weight);
                        })
                .asConstraint("Room capacity efficiency");
    }

    private Constraint studentFatigue(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentGroup),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        lesson1.getStudentGroup() != null &&
                        areConsecutive(lesson1, lesson2))
                .penalize(HardSoftScore.ofSoft(getWeightStudentFatigue()))
                .asConstraint("Student fatigue");
    }

    private Constraint lecturerRoomTransition(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getLecturer),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> lesson1.getLecturer() != null &&
                        lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        lesson1.getRoom() != null &&
                        lesson2.getRoom() != null &&
                        areConsecutive(lesson1, lesson2) &&
                        !Objects.equals(lesson1.getRoom().getId(), lesson2.getRoom().getId()))
                .penalize(HardSoftScore.ofSoft(getWeightLecturerTransition()),
                        (lesson1, lesson2) -> {
                            if (lesson1.getRoom().getZone() != null &&
                                    lesson2.getRoom().getZone() != null &&
                                    !Objects.equals(lesson1.getRoom().getZone().getId(),
                                            lesson2.getRoom().getZone().getId())) {
                                return 3;
                            }
                            return 1;
                        })
                .asConstraint("Lecturer room transition");
    }

    private Constraint dayBalanceForStudentGroup(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentGroup),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> isDayBalanceEnforced() &&
                        lesson1.getStudentGroup() != null &&
                        lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null)
                .penalize(HardSoftScore.ofSoft(getWeightDayBalance()))
                .asConstraint("Day balance for student group");
    }

    /**
     * SOFT CONSTRAINT: Early Morning Penalty
     * Penalize lessons starting at 7am - students prefer later starts.
     * This is a soft constraint so 7am is still used if necessary.
     */
    private Constraint earlyMorningPenalty(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null &&
                        lesson.getTimeslot().getStartTime().equals(LocalTime.of(7, 0)))
                .penalize(HardSoftScore.ofSoft(getWeightEarlyMorning()))
                .asConstraint("Early morning penalty");
    }

    /**
     * SOFT: Penalize when lecturers have consecutive teaching hours.
     * Similar to student fatigue but for lecturers.
     */
    private Constraint lecturerFatigue(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getLecturer),
                Joiners.equal(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek() : null))
                .filter((lesson1, lesson2) -> lesson1.getTimeslot() != null &&
                        lesson2.getTimeslot() != null &&
                        lesson1.getLecturer() != null &&
                        areConsecutive(lesson1, lesson2))
                .penalize(HardSoftScore.ofSoft(getWeightStudentFatigue())) // Reuse student fatigue weight
                .asConstraint("Lecturer fatigue");
    }

    private int getMaxLecturerConsecutiveHours() {
        initializeSettings();
        return cachedMaxLecturerConsecutiveHours;
    }

    // ==================== HELPER METHODS ====================

    private boolean intervalsOverlap(Lesson lesson1, Lesson lesson2) {
        LocalTime start1 = lesson1.getTimeslot().getStartTime();
        LocalTime end1 = lesson1.getEndTime();
        LocalTime start2 = lesson2.getTimeslot().getStartTime();
        LocalTime end2 = lesson2.getEndTime();

        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    private boolean areConsecutive(Lesson lesson1, Lesson lesson2) {
        LocalTime end1 = lesson1.getEndTime();
        LocalTime start2 = lesson2.getTimeslot().getStartTime();
        LocalTime end2 = lesson2.getEndTime();
        LocalTime start1 = lesson1.getTimeslot().getStartTime();

        boolean consecutive = end1.equals(start2) || end2.equals(start1);

        if (consecutive && isLunchBreakEnforced()) {
            LocalTime earlierEnd = end1.isBefore(end2) ? end1 : end2;
            LocalTime laterStart = start1.isAfter(start2) ? start1 : start2;

            LocalTime lunchStart = getLunchBreakStart();
            LocalTime lunchEnd = getLunchBreakEnd();

            if (earlierEnd.equals(lunchStart) && laterStart.equals(lunchEnd)) {
                return false;
            }
        }

        return consecutive;
    }
}
