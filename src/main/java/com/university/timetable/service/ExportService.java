package com.university.timetable.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles timetable exports to Excel (.xlsx) and PDF formats.
 * Supports filtering by specific groups, departments (parent groups), or all
 * groups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final LessonRepository lessonRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TimeslotRepository timeslotRepository;
    private final ConstraintSettingsService settingsService;

    private static final DayOfWeek[] WEEKDAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Export timetable to Excel (.xlsx) format.
     * Creates one sheet per student group.
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel(List<Long> groupIds, String title) throws Exception {
        List<StudentGroup> groups = resolveGroups(groupIds);
        List<Lesson> allLessons = lessonRepository.findAll();
        List<Timeslot> timeslots = timeslotRepository.findAll();

        // Sort timeslots by time
        timeslots.sort(Comparator.comparing(Timeslot::getStartTime));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle timeStyle = createTimeStyle(workbook);
            CellStyle lessonStyle = createLessonStyle(workbook);
            CellStyle emptyStyle = createEmptyStyle(workbook);
            CellStyle lunchBreakStyle = createLunchBreakStyle(workbook);

            // Overview sheet
            createOverviewSheet(workbook, groups, title, headerStyle);

            // One sheet per group
            for (StudentGroup group : groups) {
                List<Lesson> groupLessons = filterLessonsByGroup(allLessons, group);
                createGroupSheet(workbook, group, groupLessons, timeslots,
                        headerStyle, timeStyle, lessonStyle, emptyStyle, lunchBreakStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            log.info("Generated Excel export with {} groups", groups.size());
            return out.toByteArray();
        }
    }

    /**
     * Export timetable to PDF format.
     */
    @Transactional(readOnly = true)
    public byte[] exportToPdf(List<Long> groupIds, String title) throws Exception {
        List<StudentGroup> groups = resolveGroups(groupIds);
        List<Lesson> allLessons = lessonRepository.findAll();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        timeslots.sort(Comparator.comparing(Timeslot::getStartTime));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
        PdfWriter.getInstance(document, out);
        document.open();

        // Title
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph titlePara = new Paragraph(title != null ? title : "Timetable Export", titleFont);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        document.add(titlePara);

        // Generated date
        Font smallFont = new Font(Font.HELVETICA, 10);
        Paragraph datePara = new Paragraph("Generated: " + LocalDate.now().toString() +
                " | Groups: " + groups.size(), smallFont);
        datePara.setAlignment(Element.ALIGN_CENTER);
        document.add(datePara);
        document.add(new Paragraph("\n"));

        // Create timetable for each group
        for (int i = 0; i < groups.size(); i++) {
            StudentGroup group = groups.get(i);
            List<Lesson> groupLessons = filterLessonsByGroup(allLessons, group);

            if (i > 0) {
                document.newPage();
            }

            // Group header
            Font groupFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Paragraph groupPara = new Paragraph(group.getName() + " (" + group.getSize() + " students)", groupFont);
            groupPara.setSpacingBefore(10);
            document.add(groupPara);

            // Timetable grid
            PdfPTable table = createPdfTable(groupLessons, timeslots);
            document.add(table);
        }

        document.close();
        log.info("Generated PDF export with {} groups", groups.size());
        return out.toByteArray();
    }

    /**
     * Get all groups, optionally expanding departments to include children.
     */
    @Transactional(readOnly = true)
    public List<StudentGroup> resolveGroups(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            // Return all groups if none specified
            return studentGroupRepository.findAll();
        }

        Set<StudentGroup> result = new LinkedHashSet<>();
        for (Long id : groupIds) {
            studentGroupRepository.findById(id).ifPresent(group -> {
                result.add(group);
                // If this is a parent group, add all children
                if (group.getChildren() != null && !group.getChildren().isEmpty()) {
                    result.addAll(group.getChildren());
                }
            });
        }
        return new ArrayList<>(result);
    }

    /**
     * Get only parent groups (departments) for UI selection.
     */
    @Transactional(readOnly = true)
    public List<StudentGroup> getDepartments() {
        return studentGroupRepository.findAll().stream()
                .filter(g -> g.getParentGroup() == null || g.getChildren().size() > 0)
                .collect(Collectors.toList());
    }

    // ========== EXCEL HELPERS ==========

    private void createOverviewSheet(XSSFWorkbook workbook, List<StudentGroup> groups,
            String title, CellStyle headerStyle) {
        XSSFSheet sheet = workbook.createSheet("Overview");
        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title != null ? title : "Timetable Export");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        rowNum++;

        // Metadata
        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Generated:");
        dateRow.createCell(1).setCellValue(LocalDate.now().toString());

        Row countRow = sheet.createRow(rowNum++);
        countRow.createCell(0).setCellValue("Groups Included:");
        countRow.createCell(1).setCellValue(groups.size());

        rowNum++;

        // Group list
        Row listHeader = sheet.createRow(rowNum++);
        listHeader.createCell(0).setCellValue("Group Name");
        listHeader.createCell(1).setCellValue("Size");
        listHeader.createCell(2).setCellValue("Department");
        listHeader.getCell(0).setCellStyle(headerStyle);
        listHeader.getCell(1).setCellStyle(headerStyle);
        listHeader.getCell(2).setCellStyle(headerStyle);

        for (StudentGroup g : groups) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(g.getName());
            row.createCell(1).setCellValue(g.getSize());
            row.createCell(2).setCellValue(g.getParentGroup() != null ? g.getParentGroup().getName() : "(Top Level)");
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
    }

    private void createGroupSheet(XSSFWorkbook workbook, StudentGroup group,
            List<Lesson> lessons, List<Timeslot> timeslots,
            CellStyle headerStyle, CellStyle timeStyle,
            CellStyle lessonStyle, CellStyle emptyStyle, CellStyle lunchBreakStyle) {
        // Sanitize sheet name (max 31 chars, no special chars)
        String sheetName = group.getName().replaceAll("[\\[\\]*/\\\\?:]", "_");
        if (sheetName.length() > 31)
            sheetName = sheetName.substring(0, 31);

        XSSFSheet sheet = workbook.createSheet(sheetName);

        // Get time range and lunch break from settings
        LocalTime earliestStart = settingsService.getEarliestStartTime();
        LocalTime latestEnd = LocalTime.of(18, 0); // Default end at 18:00
        LocalTime lunchStart = settingsService.getLunchBreakStart();
        LocalTime lunchEnd = settingsService.getLunchBreakEnd();

        // Generate ALL hours from earliest to latest
        List<LocalTime> allHours = new ArrayList<>();
        LocalTime current = earliestStart;
        while (!current.isAfter(latestEnd.minusHours(1))) {
            allHours.add(current);
            current = current.plusHours(1);
        }

        // Header row: Time | Monday | Tuesday | ...
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Time");
        headerRow.getCell(0).setCellStyle(headerStyle);

        for (int d = 0; d < WEEKDAYS.length; d++) {
            Cell cell = headerRow.createCell(d + 1);
            cell.setCellValue(WEEKDAYS[d].toString());
            cell.setCellStyle(headerStyle);
        }

        // Build a map of timeslot -> lesson (keyed by day and start time)
        Map<String, Lesson> lessonMap = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() != null) {
                String key = lesson.getTimeslot().getDayOfWeek() + "_" +
                        lesson.getTimeslot().getStartTime();
                lessonMap.put(key, lesson);
            }
        }

        // Track which cells are already covered by multi-hour lessons
        Set<String> coveredCells = new HashSet<>();

        // Create all rows first
        for (int i = 0; i <= allHours.size(); i++) {
            sheet.createRow(i + 1);
        }

        // Data rows - show ALL hours
        int excelRowNum = 1;
        for (int timeIdx = 0; timeIdx < allHours.size(); timeIdx++) {
            LocalTime time = allHours.get(timeIdx);
            Row row = sheet.getRow(excelRowNum);

            // Check if this hour is during lunch break
            boolean isLunchHour = !time.isBefore(lunchStart) && time.isBefore(lunchEnd);

            Cell timeCell = row.createCell(0);
            timeCell.setCellValue(time.format(TIME_FORMATTER));
            timeCell.setCellStyle(isLunchHour ? lunchBreakStyle : timeStyle);

            for (int d = 0; d < WEEKDAYS.length; d++) {
                String cellKey = WEEKDAYS[d] + "_" + timeIdx;

                // Skip if this cell is covered by a multi-hour lesson from above
                if (coveredCells.contains(cellKey)) {
                    continue;
                }

                Cell cell = row.createCell(d + 1);

                // If it's lunch hour, show "LUNCH BREAK" for all days
                if (isLunchHour) {
                    cell.setCellValue("LUNCH BREAK");
                    cell.setCellStyle(lunchBreakStyle);
                    continue;
                }

                String key = WEEKDAYS[d] + "_" + time;
                Lesson lesson = lessonMap.get(key);

                if (lesson != null) {
                    // Build content - Check online FIRST
                    String content = lesson.getCourse().getCode();
                    int duration = lesson.getDurationHours();
                    if (duration > 1) {
                        content += " (" + duration + "h)";
                    }

                    // Location - ONLINE takes priority
                    if (lesson.isOnline()) {
                        content += "\nOnline";
                    } else if (lesson.getRoom() != null) {
                        content += "\n" + lesson.getRoom().getName();
                    }

                    // Lecturer
                    if (lesson.getLecturer() != null) {
                        content += "\n" + lesson.getLecturer().getName();
                    }

                    cell.setCellValue(content);
                    cell.setCellStyle(lessonStyle);

                    // Merge cells for multi-hour lessons
                    if (duration > 1 && timeIdx + duration <= allHours.size()) {
                        int endRow = excelRowNum + duration - 1;
                        sheet.addMergedRegion(new CellRangeAddress(excelRowNum, endRow, d + 1, d + 1));

                        // Mark covered cells
                        for (int h = 1; h < duration; h++) {
                            coveredCells.add(WEEKDAYS[d] + "_" + (timeIdx + h));
                        }

                        // Apply style to merged area cells
                        for (int h = 1; h < duration && (timeIdx + h) < allHours.size(); h++) {
                            Row futureRow = sheet.getRow(excelRowNum + h);
                            if (futureRow != null) {
                                Cell futureCell = futureRow.createCell(d + 1);
                                futureCell.setCellStyle(lessonStyle);
                            }
                        }
                    }
                } else {
                    cell.setCellStyle(emptyStyle);
                }
            }
            excelRowNum++;
        }

        // Set row height to accommodate content
        for (int i = 1; i <= allHours.size(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                row.setHeightInPoints(45);
            }
        }

        // Set column widths
        sheet.setColumnWidth(0, 3000);
        for (int d = 0; d < WEEKDAYS.length; d++) {
            sheet.setColumnWidth(d + 1, 5500);
        }
    }

    private List<Lesson> filterLessonsByGroup(List<Lesson> lessons, StudentGroup group) {
        return lessons.stream()
                .filter(l -> {
                    if (l.getCourse() == null)
                        return false;
                    return l.getCourse().getAllStudentGroups().contains(group);
                })
                .collect(Collectors.toList());
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTimeStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createLessonStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createEmptyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createLunchBreakStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ========== PDF HELPERS ==========

    private PdfPTable createPdfTable(List<Lesson> lessons, List<Timeslot> timeslots) {
        PdfPTable table = new PdfPTable(6); // Time + 5 days
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        try {
            table.setWidths(new float[] { 1f, 2f, 2f, 2f, 2f, 2f });
        } catch (Exception e) {
            log.warn("Could not set table widths", e);
        }

        // Header
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        addPdfCell(table, "Time", headerFont, new Color(0, 51, 102), 1);
        for (DayOfWeek day : WEEKDAYS) {
            addPdfCell(table, day.toString(), headerFont, new Color(0, 51, 102), 1);
        }

        // Get time range and lunch break from settings
        LocalTime earliestStart = settingsService.getEarliestStartTime();
        LocalTime latestEnd = LocalTime.of(18, 0);
        LocalTime lunchStart = settingsService.getLunchBreakStart();
        LocalTime lunchEnd = settingsService.getLunchBreakEnd();

        // Generate ALL hours from earliest to latest
        List<LocalTime> allHours = new ArrayList<>();
        LocalTime current = earliestStart;
        while (!current.isAfter(latestEnd.minusHours(1))) {
            allHours.add(current);
            current = current.plusHours(1);
        }

        // Lunch break styling
        Font lunchFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        Color lunchColor = new Color(255, 140, 0); // Orange

        // Build lesson map
        Map<String, Lesson> lessonMap = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() != null) {
                String key = lesson.getTimeslot().getDayOfWeek() + "_" +
                        lesson.getTimeslot().getStartTime();
                lessonMap.put(key, lesson);
            }
        }

        // Track covered cells for multi-hour lessons
        Set<String> coveredCells = new HashSet<>();

        // Data rows - show ALL hours
        Font timeFont = new Font(Font.HELVETICA, 9, Font.BOLD);
        Font lessonFont = new Font(Font.HELVETICA, 8);

        for (int timeIdx = 0; timeIdx < allHours.size(); timeIdx++) {
            LocalTime time = allHours.get(timeIdx);

            // Check if this hour is during lunch break
            boolean isLunchHour = !time.isBefore(lunchStart) && time.isBefore(lunchEnd);

            // Time column
            if (isLunchHour) {
                addPdfCell(table, time.format(TIME_FORMATTER), lunchFont, lunchColor, 1);
            } else {
                addPdfCell(table, time.format(TIME_FORMATTER), timeFont, new Color(240, 240, 240), 1);
            }

            for (int d = 0; d < WEEKDAYS.length; d++) {
                DayOfWeek day = WEEKDAYS[d];
                String cellKey = day + "_" + timeIdx;

                // Skip if covered by multi-hour lesson
                if (coveredCells.contains(cellKey)) {
                    continue;
                }

                // If it's lunch hour, show "LUNCH BREAK" for all days
                if (isLunchHour) {
                    addPdfCell(table, "LUNCH BREAK", lunchFont, lunchColor, 1);
                    continue;
                }

                String key = day + "_" + time;
                Lesson lesson = lessonMap.get(key);

                if (lesson != null) {
                    int duration = lesson.getDurationHours();

                    // Build content - Check online FIRST
                    String content = lesson.getCourse().getCode();
                    if (duration > 1) {
                        content += " (" + duration + "h)";
                    }

                    // Location - ONLINE takes priority
                    if (lesson.isOnline()) {
                        content += "\nOnline";
                    } else if (lesson.getRoom() != null) {
                        content += "\n" + lesson.getRoom().getName();
                    }

                    // Lecturer name
                    if (lesson.getLecturer() != null) {
                        content += "\n" + lesson.getLecturer().getName();
                    }

                    // Calculate row span for multi-hour lessons
                    int rowSpan = Math.min(duration, allHours.size() - timeIdx);
                    addPdfCell(table, content, lessonFont, new Color(200, 255, 200), rowSpan);

                    // Mark covered cells
                    for (int h = 1; h < rowSpan; h++) {
                        coveredCells.add(day + "_" + (timeIdx + h));
                    }
                } else {
                    addPdfCell(table, "", lessonFont, Color.WHITE, 1);
                }
            }
        }

        return table;
    }

    private void addPdfCell(PdfPTable table, String text, Font font, Color bgColor, int rowSpan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        if (rowSpan > 1) {
            cell.setRowspan(rowSpan);
        }
        table.addCell(cell);
    }
}
