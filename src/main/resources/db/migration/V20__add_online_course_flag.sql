-- Add is_online flag to course table
-- Online courses don't require physical rooms and have no capacity limits
ALTER TABLE course ADD COLUMN is_online BOOLEAN DEFAULT FALSE;
