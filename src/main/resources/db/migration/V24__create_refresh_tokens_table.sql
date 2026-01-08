-- V24: Create refresh tokens table and default admin user

-- Refresh tokens for JWT authentication
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    
    INDEX idx_refresh_token (token),
    INDEX idx_refresh_user (user_id),
    INDEX idx_refresh_expires (expires_at),
    
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

-- Create default super admin user
-- Password: Admin@123 (BCrypt hash with cost 12)
INSERT INTO users (
    email, 
    password_hash, 
    first_name, 
    last_name, 
    role, 
    active, 
    email_verified,
    password_changed_at
) VALUES (
    'admin@babcock.edu.ng',
    '$2a$12$bt/9cAJG/00e91lrppfpSOQP1MUjYnF5wB4yBbr9vvkgPC3sxzECq',
    'System',
    'Administrator',
    'SUPER_ADMIN',
    TRUE,
    TRUE,
    NOW()
);
