package com.example.repository;

import com.example.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Integer> {
    // Spring Boot tự động hiểu: SELECT * FROM Rooms WHERE CinemaId = ?
    List<Room> findByCinemaId(Integer cinemaId);
}