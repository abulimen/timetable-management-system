-- Add user_id foreign key to lecturer table
-- This links lecturers to their user accounts for authentication and profile management

ALTER TABLE lecturer ADD COLUMN user_id BIGINT NULL;

ALTER TABLE lecturer ADD CONSTRAINT fk_lecturer_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Create index for faster lookups
CREATE INDEX idx_lecturer_user_id ON lecturer(user_id);
