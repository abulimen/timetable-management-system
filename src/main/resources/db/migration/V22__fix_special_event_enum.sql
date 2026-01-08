-- Fix day_of_week column type to match Hibernate ENUM expectation
ALTER TABLE special_event MODIFY COLUMN day_of_week ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL;
