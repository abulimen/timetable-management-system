-- User Authentication Tables
-- V23: Create users table for authentication and authorization

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Authentication
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Profile
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    department VARCHAR(100),
    
    -- Authorization
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    
    -- Link to Lecturer (if applicable, enforced by JPA not DB constraint)
    lecturer_id BIGINT,
    
    -- Status
    active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    last_login_at DATETIME(3),
    
    -- Security
    failed_login_attempts INT DEFAULT 0,
    locked_until DATETIME(3),
    password_changed_at DATETIME(3),
    must_change_password BOOLEAN DEFAULT FALSE,
    
    -- Indexes
    INDEX idx_users_email (email),
    INDEX idx_users_role (role),
    INDEX idx_users_active (active),
    INDEX idx_users_lecturer_id (lecturer_id)
);
