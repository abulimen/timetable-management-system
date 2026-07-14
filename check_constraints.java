// Quick analysis of constraint tightness
import java.sql.*;

public class check_constraints {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/timetable?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String pass = "password";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            // Check lecturer load vs available slots
            System.out.println("=== LECTURER LOAD ANALYSIS ===");
            var rs = conn.createStatement().executeQuery("""
                SELECT l.name, COUNT(le.id) as lessons, 
                       SUM(le.duration_hours) as hours,
                       COUNT(DISTINCT le.course_id) as courses
                FROM lecturer l
                LEFT JOIN lesson le ON l.id = le.lecturer_id
                GROUP BY l.id, l.name
                HAVING hours > 20
                ORDER BY hours DESC
                LIMIT 20
            """);
            while (rs.next()) {
                System.out.printf("%s: %d lessons, %d hours, %d courses%n",
                    rs.getString("name"), rs.getInt("lessons"), 
                    rs.getInt("hours"), rs.getInt("courses"));
            }
            
            // Check student group conflicts
            System.out.println("\n=== STUDENT GROUP SCHEDULE DENSITY ===");
            rs = conn.createStatement().executeQuery("""
                SELECT sg.name, sg.size, COUNT(DISTINCT c.id) as courses,
                       SUM(c.weekly_hours) as total_hours
                FROM student_group sg
                JOIN course_student_group csg ON sg.id = csg.student_group_id
                JOIN course c ON csg.course_id = c.id
                GROUP BY sg.id
                HAVING total_hours > 40
                ORDER BY total_hours DESC
                LIMIT 20
            """);
            while (rs.next()) {
                System.out.printf("%s (size=%d): %d courses, %d total hours%n",
                    rs.getString("name"), rs.getInt("size"),
                    rs.getInt("courses"), rs.getInt("total_hours"));
            }
            
            // Check for courses with many student groups (combined classes)
            System.out.println("\n=== COMBINED CLASSES ===");
            rs = conn.createStatement().executeQuery("""
                SELECT c.code, COUNT(DISTINCT csg.student_group_id) as groups,
                       SUM(sg.size) as total_students
                FROM course c
                JOIN course_student_group csg ON c.id = csg.course_id
                JOIN student_group sg ON csg.student_group_id = sg.id
                GROUP BY c.id
                HAVING groups > 1
                ORDER BY total_students DESC
                LIMIT 20
            """);
            while (rs.next()) {
                System.out.printf("%s: %d groups, %d total students%n",
                    rs.getString("code"), rs.getInt("groups"), rs.getInt("total_students"));
            }
        }
    }
}
