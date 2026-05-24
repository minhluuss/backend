package com.example.repository;

import com.example.entity.Showtime;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.roomId = :roomId " +
        "AND s.startTime < :endTime AND s.endTime > :startTime")
    long countOverlappingShowtimes(
        @Param("roomId") Integer roomId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.roomId = :roomId " +
        "AND s.id <> :id AND s.startTime < :endTime AND s.endTime > :startTime")
    long countOverlappingShowtimesExcludingId(
        @Param("id") Integer id,
        @Param("roomId") Integer roomId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);
}