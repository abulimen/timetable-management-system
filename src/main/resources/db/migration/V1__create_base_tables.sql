-- V1: Create base tables (zones, features, rooms)
-- Based on design.md database schema

-- Zones (Building/Block groupings)
CREATE TABLE zone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Features (Room capabilities)
CREATE TABLE feature (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Rooms
CREATE TABLE room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    zone_id BIGINT,
    FOREIGN KEY (zone_id) REFERENCES zone(id)
);

-- Room-Feature junction table
CREATE TABLE room_feature (
    room_id BIGINT,
    feature_id BIGINT,
    PRIMARY KEY (room_id, feature_id),
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
    FOREIGN KEY (feature_id) REFERENCES feature(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_room_zone ON room(zone_id);
CREATE INDEX idx_room_capacity ON room(capacity);
