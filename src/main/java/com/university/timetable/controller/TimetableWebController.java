package com.university.timetable.controller;

import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.repository.StudentGroupRepository;
import com.university.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/view")
@RequiredArgsConstructor
public class TimetableWebController {

    private final TimetableService timetableService;
    private final StudentGroupRepository studentGroupRepository;

    @GetMapping("/departments")
    @ResponseBody
    public String listDepartments() {
        List<String> groups = studentGroupRepository.findAll().stream()
            .map(g -> g.getName())
            .filter(name -> name.contains("_"))
            .map(name -> name.split("_")[0])
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>BUTMS Timetables</title>");
        html.append("<style>");
        html.append("* { box-sizing: border-box; }");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 1100px; margin: 0 auto; padding: 30px; background: #f5f7fa; }");
        html.append("h1 { color: #1a365d; margin-bottom: 10px; }");
        html.append(".dept-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; margin-top: 20px; }");
        html.append(".dept-card { background: #fff; padding: 16px; border-radius: 8px; border: 1px solid #e2e8f0; transition: all 0.2s; }");
        html.append(".dept-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }");
        html.append(".dept-card a { color: #2c5282; text-decoration: none; font-weight: 600; }");
        html.append(".stats { color: #718096; margin-bottom: 20px; }");
        html.append("</style></head><body>");
        
        html.append("<h1>🎓 BUTMS Timetables</h1>");
        html.append("<p class='stats'>").append(groups.size()).append(" departments • ")
            .append(timetableService.getAllLessons().size()).append(" lessons</p>");
        
        html.append("<div class='dept-grid'>");
        for (String dept : groups) {
            html.append("<div class='dept-card'><a href='/view/timetable?dept=").append(dept).append("'>").append(dept).append("</a></div>");
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    @GetMapping("/timetable")
    @ResponseBody
    public String viewTimetable(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String group) {
            
        List<TimetableViewDTO> allLessons = timetableService.getAllLessons();
        
        List<TimetableViewDTO> filteredLessons = allLessons.stream()
            .filter(l -> {
                if (l.getStudentGroupName() == null) return false;
                String[] parts = l.getStudentGroupName().split("_");
                if (parts.length < 3) return false;
                boolean match = true;
                if (dept != null && !dept.isEmpty()) match &= parts[0].equals(dept);
                if (level != null && !level.isEmpty()) match &= parts[1].equals(level);
                if (group != null && !group.isEmpty()) match &= parts[2].equals(group);
                return match;
            })
            .collect(Collectors.toList());

        Set<String> depts = new TreeSet<>();
        Set<String> levels = new TreeSet<>();
        Set<String> groups = new TreeSet<>();
        
        allLessons.stream().map(TimetableViewDTO::getStudentGroupName).filter(Objects::nonNull).forEach(name -> {
            String[] parts = name.split("_");
            if (parts.length >= 3) {
                depts.add(parts[0]);
                if (dept != null && parts[0].equals(dept)) {
                    levels.add(parts[1]);
                    if (level != null && parts[1].equals(level)) {
                        groups.add(parts[2]);
                    }
                }
            }
        });

        // Calculate layout positions for overlapping lessons
        Map<TimetableViewDTO, int[]> lessonLayout = calculateOverlapLayout(filteredLessons);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Timetable</title>");
        html.append("<style>");
        html.append("* { box-sizing: border-box; margin: 0; padding: 0; }");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f8fafc; }");
        
        // Header
        html.append(".header { background: #fff; padding: 12px 24px; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center; }");
        html.append(".brand { font-weight: 700; color: #1a365d; text-decoration: none; }");
        html.append(".breadcrumb { color: #64748b; font-size: 0.9em; }");
        html.append(".filters { display: flex; gap: 8px; }");
        html.append("select { padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; font-size: 0.9em; }");
        html.append(".clear { color: #ef4444; text-decoration: none; font-size: 0.85em; margin-left: 8px; }");
        
        // Timetable
        html.append(".timetable-wrap { padding: 20px; overflow-x: auto; }");
        html.append(".timetable { display: grid; grid-template-columns: 60px repeat(5, minmax(180px, 1fr)); background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; min-width: 1100px; }");
        html.append(".t-head { background: #1e3a5f; color: #fff; font-weight: 600; text-align: center; padding: 12px 8px; font-size: 0.9em; }");
        html.append(".t-time { background: #f1f5f9; color: #475569; font-size: 0.8em; font-weight: 500; display: flex; align-items: flex-start; justify-content: center; padding-top: 6px; border-right: 1px solid #e2e8f0; min-height: 100px; }");
        html.append(".t-cell { position: relative; min-height: 100px; border-bottom: 1px solid #f1f5f9; border-right: 1px solid #f1f5f9; }");
        
        // Lessons
        html.append(".lesson { position: absolute; top: 2px; border-radius: 4px; padding: 6px 8px; font-size: 0.8em; overflow: hidden; border-left: 4px solid; }");
        html.append(".lesson .code { font-weight: 700; font-size: 1.05em; margin-bottom: 2px; }");
        html.append(".lesson .name { opacity: 0.9; margin-bottom: 3px; line-height: 1.2; }");
        html.append(".lesson .room { color: #dc2626; font-weight: 600; margin-bottom: 2px; }");
        html.append(".lesson .grp { opacity: 0.7; font-size: 0.9em; }");
        
        // Colors
        String[] colors = {"#ef4444", "#f97316", "#eab308", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899"};
        String[] bgColors = {"#fef2f2", "#fff7ed", "#fefce8", "#f0fdf4", "#eff6ff", "#f5f3ff", "#fdf2f8"};
        for (int i = 0; i < colors.length; i++) {
            html.append(".c").append(i).append(" { background: ").append(bgColors[i]).append("; border-color: ").append(colors[i]).append("; }");
        }
        html.append("</style></head><body>");
        
        // Header
        html.append("<div class='header'>");
        html.append("<div><a href='/view/departments' class='brand'>🎓 BUTMS</a>");
        html.append("<span class='breadcrumb'>");
        if (dept != null) html.append(" / ").append(dept);
        if (level != null) html.append(" / Year ").append(level);
        if (group != null) html.append(" / Group ").append(group);
        html.append("</span></div>");
        
        html.append("<div class='filters'><form method='get' style='display:flex; gap:8px;'>");
        html.append("<select name='dept' onchange='this.form.submit()'><option value=''>Dept</option>");
        for (String d : depts) html.append("<option value='").append(d).append("'").append(d.equals(dept) ? " selected" : "").append(">").append(d).append("</option>");
        html.append("</select>");
        
        if (dept != null) {
            html.append("<select name='level' onchange='this.form.submit()'><option value=''>Year</option>");
            for (String l : levels) html.append("<option value='").append(l).append("'").append(l.equals(level) ? " selected" : "").append(">").append(l).append("</option>");
            html.append("</select>");
        }
        if (level != null) {
            html.append("<select name='group' onchange='this.form.submit()'><option value=''>Group</option>");
            for (String g : groups) html.append("<option value='").append(g).append("'").append(g.equals(group) ? " selected" : "").append(">").append(g).append("</option>");
            html.append("</select>");
        }
        if (dept != null) html.append("<a href='/view/timetable' class='clear'>Clear</a>");
        html.append("</form></div></div>");
        
        // Timetable grid
        html.append("<div class='timetable-wrap'><div class='timetable'>");
        
        // Headers
        html.append("<div class='t-head'>TIME</div>");
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        for (String day : days) html.append("<div class='t-head'>").append(day).append("</div>");
        
        // Time rows 7-19
        for (int hour = 7; hour <= 19; hour++) {
            html.append("<div class='t-time'>").append(String.format("%02d:00", hour)).append("</div>");
            
            for (int col = 0; col < 5; col++) {
                html.append("<div class='t-cell'>");
                
                // Render lessons starting at this hour
                for (TimetableViewDTO l : filteredLessons) {
                    if (l.getDayOfWeek() == null || l.getStartTime() == null) continue;
                    if (!l.getDayOfWeek().toString().equals(days[col])) continue;
                    if (l.getStartTime().getHour() != hour) continue;
                    
                    int[] layout = lessonLayout.getOrDefault(l, new int[]{0, 1});
                    int position = layout[0];
                    int total = layout[1];
                    
                    int duration = 1;
                    if (l.getEndTime() != null) {
                        duration = Math.max(1, l.getEndTime().getHour() - l.getStartTime().getHour());
                    }
                    
                    int heightPx = duration * 100 - 4;
                    double widthPct = 100.0 / total;
                    double leftPct = position * widthPct;
                    int colorIdx = Math.abs(l.getCourseCode().hashCode()) % 7;
                    
                    html.append("<div class='lesson c").append(colorIdx).append("' style='")
                        .append("height:").append(heightPx).append("px;")
                        .append("width:calc(").append(String.format("%.1f", widthPct)).append("% - 2px);")
                        .append("left:calc(").append(String.format("%.1f", leftPct)).append("% + 1px);'>");
                    
                    html.append("<div class='code'>").append(l.getCourseCode()).append("</div>");
                    html.append("<div class='name'>").append(l.getCourseName()).append("</div>");
                    html.append("<div class='room'>📍 ").append(l.getRoomName() != null ? l.getRoomName() : "TBA").append("</div>");
                    html.append("<div class='grp'>").append(l.getStudentGroupName()).append("</div>");
                    html.append("</div>");
                }
                
                html.append("</div>");
            }
        }
        
        html.append("</div></div></body></html>");
        return html.toString();
    }
    
    /**
     * Calculate layout positions for overlapping lessons.
     * Returns map of lesson -> [position, totalInCluster]
     */
    private Map<TimetableViewDTO, int[]> calculateOverlapLayout(List<TimetableViewDTO> lessons) {
        Map<TimetableViewDTO, int[]> result = new HashMap<>();
        
        // Group by day
        Map<String, List<TimetableViewDTO>> byDay = lessons.stream()
            .filter(l -> l.getDayOfWeek() != null && l.getStartTime() != null && l.getEndTime() != null)
            .collect(Collectors.groupingBy(l -> l.getDayOfWeek().toString()));
        
        for (List<TimetableViewDTO> dayLessons : byDay.values()) {
            // Sort by start time
            dayLessons.sort(Comparator.comparing(TimetableViewDTO::getStartTime));
            
            // Find overlapping clusters
            List<List<TimetableViewDTO>> clusters = new ArrayList<>();
            
            for (TimetableViewDTO lesson : dayLessons) {
                // Find which cluster this lesson belongs to
                List<TimetableViewDTO> targetCluster = null;
                
                for (List<TimetableViewDTO> cluster : clusters) {
                    for (TimetableViewDTO existing : cluster) {
                        if (overlaps(existing, lesson)) {
                            targetCluster = cluster;
                            break;
                        }
                    }
                    if (targetCluster != null) break;
                }
                
                if (targetCluster != null) {
                    targetCluster.add(lesson);
                } else {
                    List<TimetableViewDTO> newCluster = new ArrayList<>();
                    newCluster.add(lesson);
                    clusters.add(newCluster);
                }
            }
            
            // Assign positions within each cluster
            for (List<TimetableViewDTO> cluster : clusters) {
                int size = cluster.size();
                for (int i = 0; i < size; i++) {
                    result.put(cluster.get(i), new int[]{i, size});
                }
            }
        }
        
        // Default for lessons not in result
        for (TimetableViewDTO l : lessons) {
            if (!result.containsKey(l)) {
                result.put(l, new int[]{0, 1});
            }
        }
        
        return result;
    }
    
    private boolean overlaps(TimetableViewDTO a, TimetableViewDTO b) {
        if (a.getStartTime() == null || a.getEndTime() == null || b.getStartTime() == null || b.getEndTime() == null) {
            return false;
        }
        // Two lessons overlap if: a.start < b.end AND b.start < a.end
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }
}
