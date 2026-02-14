CREATE TABLE IF NOT EXISTS solver_run_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    best_score VARCHAR(64),
    best_hard_score INT,
    best_soft_score INT,
    lessons_count INT,
    timeslots_count INT,
    rooms_count INT,
    improvement_count BIGINT NOT NULL DEFAULT 0,
    persistence_count BIGINT NOT NULL DEFAULT 0,
    avg_persistence_ms BIGINT,
    duration_ms BIGINT,
    time_to_first_best_ms BIGINT,
    error_message VARCHAR(512),
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_solver_run_metric_started_at ON solver_run_metric(started_at);
CREATE INDEX idx_solver_run_metric_mode_started_at ON solver_run_metric(mode, started_at);
CREATE INDEX idx_solver_run_metric_status_started_at ON solver_run_metric(status, started_at);
