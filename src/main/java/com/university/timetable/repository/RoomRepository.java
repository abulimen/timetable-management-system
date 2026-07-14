package com.university.timetable.repository;

import com.university.timetable.domain.Room;
import com.university.timetable.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByName(String name);
    List<Room> findByZone(Zone zone);
    List<Room> findByCapacityGreaterThanEqual(int minCapacity);

    // Fetch all rooms with features eagerly loaded
    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.features")
    List<Room> findAllWithFeatures();
}
