# Setup Guide

Complete installation and configuration guide for the University Timetable Scheduling Engine.

---

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java JDK** | 17+ | Runtime environment |
| **Maven** | 3.8+ | Build tool |
| **MySQL** | 8.0+ | Database |
| **Git** | Any | Version control |

### Verify Installation

```bash
# Java
java -version   # Should show 17 or higher

# Maven
mvn -version    # Should show 3.8 or higher

# MySQL
mysql --version # Should show 8.0 or higher
```

---

## Installation Steps

### 1. Clone Repository

```bash
git clone <repository-url>
cd BUTMS
```

### 2. Create Database

```bash
# Login to MySQL
mysql -u root -p

# Create database
CREATE DATABASE timetable_db;
EXIT;
```

### 3. Configure Application

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/timetable_db
    username: root        # Change if needed
    password: ""          # Add your password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false       # Set true for debugging
  flyway:
    enabled: true
    baseline-on-migrate: true
```

### 4. Build Application

```bash
mvn clean install -DskipTests
```

### 5. Run Backend

```bash
mvn spring-boot:run
```

Backend API starts on `http://localhost:8080`

### 6. Run Frontend (Optional)

```bash
cd frontend
npm install
npm start
```

Frontend starts on `http://localhost:4200`

---

## Verification

### Health Check

```bash
curl http://localhost:8080/api/v1/solver/status
```

Expected response:
```json
{
  "jobId": null,
  "state": "NOT_SOLVING",
  "score": "N/A"
}
```

### Check Data Initialization

The application initializes sample data on first run. Check:

```bash
curl http://localhost:8080/api/v1/timetable | python3 -m json.tool | head -20
```

---

## Configuration Options

### Solver Settings

Located in `src/main/resources/solver-config.xml`:

| Setting | Default | Description |
|---------|---------|-------------|
| `secondsSpentLimit` | 30 | Solver timeout in seconds |
| `entityTabuSize` | 7 | Tabu search memory size |
| `constructionHeuristicType` | FIRST_FIT | Initial solution strategy |

**Example: Increase solving time to 60 seconds:**

```xml
<termination>
  <secondsSpentLimit>60</secondsSpentLimit>
</termination>
```

### Constraint Settings (Database)

These are stored in the `constraint_setting` table and can be modified via API:

```bash
# View all settings
curl http://localhost:8080/api/v1/settings

# Update lunch break end time
curl -X PUT http://localhost:8080/api/v1/settings/lunch_break_end \
  -H "Content-Type: application/json" \
  -d '{"value": "14:00"}'
```

| Key | Default | Description |
|-----|---------|-------------|
| `lunch_break_start` | 12:00 | Start of lunch period |
| `lunch_break_end` | 13:00 | End of lunch period |
| `latest_end_time` | 18:00 | Latest lesson end (Mon-Thu) |
| `friday_latest_end_time` | 12:00 | Latest lesson end (Friday) |
| `earliest_start_time` | 07:00 | Earliest lesson start |
| `enforce_lunch_break` | true | Enable lunch constraint |

---

## Running in Production

### Build JAR

```bash
mvn clean package -DskipTests
```

Creates `target/timetable-engine-1.0.0-SNAPSHOT.jar`

### Run JAR

```bash
java -jar target/timetable-engine-1.0.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mysql://prod-db:3306/timetable_db \
  --spring.datasource.password=<password>
```

### Environment Variables

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/timetable_db
export SPRING_DATASOURCE_USERNAME=timetable_user
export SPRING_DATASOURCE_PASSWORD=secure_password

java -jar timetable-engine.jar
```

### Docker (Optional)

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/timetable-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Build and run:

```bash
docker build -t timetable-engine .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/timetable_db \
  timetable-engine
```

---

## Data Import

### Excel Import

Prepare an Excel file with these sheets:

| Sheet | Required Columns |
|-------|------------------|
| Zones | name, code |
| Features | name |
| Rooms | name, capacity, zone_code, features (comma-separated) |
| Lecturers | name, email |
| StudentGroups | name, size, parent_group_name |
| Courses | code, name, weekly_hours, lecturer_email, student_group_name, features |

Upload via API:

```bash
curl -X POST http://localhost:8080/api/v1/import/upload \
  -F "file=@university_data.xlsx"
```

### Sample Data Reset

To reset to sample data:

```bash
# Drop and recreate database
mysql -u root -e "DROP DATABASE timetable_db; CREATE DATABASE timetable_db;"

# Restart application (Flyway runs migrations including sample data)
mvn spring-boot:run
```

---

## Troubleshooting

### Application Won't Start

**Error:** `Could not create connection to database server`

**Solution:** Check MySQL is running and credentials are correct:

```bash
mysql -u root -p -e "SELECT 1"
```

---

**Error:** `Port 8080 already in use`

**Solution:** Kill process or change port:

```bash
# Kill process
lsof -ti:8080 | xargs kill -9

# Or change port in application.yml
server:
  port: 8081
```

---

### Solver Not Finding Valid Solution

**Problem:** Score is `-Xhard` (negative hard score)

**Solution:** 

1. Run feasibility check:
```bash
curl http://localhost:8080/api/v1/solver/feasibility
```

2. Check constraint violations:
```bash
curl http://localhost:8080/api/v1/solver/analysis
```

3. Address blocking issues identified.

---

### Settings Not Loading

**Problem:** Solver uses hardcoded defaults instead of DB values

**Solution:** Refresh settings cache:

```bash
curl -X POST http://localhost:8080/api/v1/settings/refresh
```

Then restart solver:

```bash
curl -X POST http://localhost:8080/api/v1/solver/solve \
  -H "Content-Type: application/json" \
  -d '{"mode": "FULL_REPLAN"}'
```

---

## Development Setup

### IDE Configuration

**IntelliJ IDEA:**
1. Open project as Maven project
2. Enable Lombok plugin
3. Set SDK to Java 17

**VS Code:**
1. Install Java Extension Pack
2. Install Lombok Annotations Support

### Running Tests

```bash
# All tests
mvn test

# Skip tests for faster builds
mvn clean install -DskipTests
```

### Hot Reload (Development)

Add to `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-devtools</artifactId>
  <scope>runtime</scope>
  <optional>true</optional>
</dependency>
```

---

## Performance Tuning

See [PERFORMANCE_OPTIMIZATION.md](PERFORMANCE_OPTIMIZATION.md) for:
- Multi-threaded solving
- Construction heuristic optimization
- Nearby selection
- Database caching

### Quick Performance Boost

Enable multi-threaded solving in `solver-config.xml`:

```xml
<solver>
  <moveThreadCount>AUTO</moveThreadCount>
  ...
</solver>
```
