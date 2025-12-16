# Contributing Guide

Thank you for your interest in contributing to the University Timetable Scheduling Engine!

---

## Getting Started

### Development Setup

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/<your-username>/BUTMS.git
   cd BUTMS
   ```

2. **Set up the backend**
   ```bash
   # Create database
   mysql -u root -e "CREATE DATABASE timetable_db;"
   
   # Run backend
   mvn spring-boot:run
   ```

3. **Set up the frontend**
   ```bash
   cd frontend
   npm install
   npm start
   ```

4. **Verify everything works**
   - Backend: http://localhost:8080/api/v1/solver/status
   - Frontend: http://localhost:4200

---

## Development Workflow

### Branching Strategy

- `main` - Production-ready code
- `develop` - Integration branch for features
- `feature/*` - New features
- `bugfix/*` - Bug fixes
- `docs/*` - Documentation updates

### Creating a Feature

1. Create a branch from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-new-feature
   ```

2. Make your changes

3. Test thoroughly:
   ```bash
   # Backend tests
   mvn test
   
   # Frontend tests
   cd frontend && npm test
   ```

4. Commit with clear messages:
   ```bash
   git commit -m "feat: add online class support"
   ```

5. Push and create a Pull Request

---

## Code Style

### Java (Backend)

- Follow standard Java conventions
- Use Lombok annotations (`@Data`, `@RequiredArgsConstructor`)
- Document public methods with Javadoc
- Use meaningful variable names

### TypeScript (Frontend)

- Use Angular style guide conventions
- Standalone components preferred
- Services injected via `inject()` function
- Use TypeScript strict mode

### Commit Messages

Follow conventional commits:

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation changes |
| `refactor` | Code refactoring |
| `test` | Adding tests |
| `chore` | Build/config changes |

Examples:
```
feat: add lecturer unavailability feature
fix: resolve room capacity overflow check
docs: update API reference for bulk import
```

---

## Adding New Features

### Adding a New Constraint

1. **Define the constraint** in `TimetableConstraintProvider.java`:
   ```java
   private Constraint myNewConstraint(ConstraintFactory factory) {
       return factory.forEach(Lesson.class)
           .filter(lesson -> /* condition */)
           .penalize(HardSoftScore.ONE_SOFT)
           .asConstraint("My new constraint");
   }
   ```

2. **Add to defineConstraints()** array

3. **Document** in `docs/CONSTRAINTS.md`

### Adding a New Entity

1. **Create domain class** in `domain/`
2. **Create repository** in `repository/`
3. **Create service** in `service/`
4. **Create controller** in `controller/`
5. **Add Flyway migration** in `resources/db/migration/`
6. **Add frontend component** in `frontend/src/app/features/`
7. **Update documentation**

### Adding a New Setting

1. **Create Flyway migration**:
   ```sql
   INSERT IGNORE INTO constraint_setting...
   ```

2. **Add getter in ConstraintSettingsService**

3. **Use in TimetableConstraintProvider**

4. **Document** in Settings section of docs

---

## Testing

### Backend Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TimetableConstraintProviderTest
```

### Frontend Tests

```bash
cd frontend

# Unit tests
npm test

# E2E tests (if configured)
npm run e2e
```

---

## Pull Request Guidelines

### Before Submitting

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] New code has tests (where applicable)
- [ ] Documentation updated
- [ ] No console errors in browser
- [ ] Flyway migrations work on fresh database

### PR Description Template

```markdown
## Summary
Brief description of changes

## Type
- [ ] Feature
- [ ] Bug fix
- [ ] Documentation
- [ ] Refactor

## Changes
- List of specific changes

## Testing
How was this tested?

## Screenshots (if UI changes)
```

---

## Database Migrations

### Creating a New Migration

1. Create file in `src/main/resources/db/migration/`:
   ```
   V{version}__description.sql
   ```
   Example: `V21__add_new_feature.sql`

2. Use MySQL-compatible syntax

3. Test on fresh database:
   ```bash
   mysql -u root -e "DROP DATABASE timetable_db; CREATE DATABASE timetable_db;"
   mvn spring-boot:run
   ```

### Migration Guidelines

- Never modify existing migrations
- Use `INSERT IGNORE` for idempotent inserts
- Always specify column names in INSERT statements
- Test rollback scenarios

---

## Documentation

When adding features, update:

1. **API_REFERENCE.md** - New endpoints
2. **CONSTRAINTS.md** - New constraints
3. **USER_GUIDE.md** - User-facing features
4. **CHANGELOG.md** - Version history
5. **README.md** - Overview updates

---

## Questions?

- Check existing [issues](https://github.com/project/issues)
- Review [documentation](docs/)
- Ask in discussions

Thank you for contributing! 🎉
