package com.example.repository;

import com.example.entity.Booking;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Integer> {

    boolean existsByShowtimeId(Integer showtimeId);

    @Query("SELECT b FROM Booking b WHERE b.showtimeId = :showtimeId AND b.status <> com.example.entity.Booking.BookingStatus.CANCELLED")
    List<Booking> findActiveByShowtimeId(@Param("showtimeId") Integer showtimeId);

    @Query(value = "SELECT b.* FROM Bookings b JOIN Showtimes s ON s.Id = b.ShowtimeId JOIN Rooms r ON r.Id = s.RoomId WHERE r.CinemaId = :cinemaId ORDER BY b.CreatedAt DESC", nativeQuery = true)
    List<Booking> findByCinemaId(@Param("cinemaId") Integer cinemaId);

    @Query(value = "SELECT b.* FROM Bookings b WHERE b.UserId = :userId ORDER BY b.CreatedAt DESC", nativeQuery = true)
    List<Booking> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);

    @Modifying
    @Query(value = "UPDATE Bookings SET Status = 'CANCELLED' WHERE ShowtimeId = :showtimeId AND Status = 'PENDING' AND CreatedAt < DATE_SUB(NOW(), INTERVAL 10 MINUTE)", nativeQuery = true)
    int cancelExpiredPendingByShowtimeId(@Param("showtimeId") Integer showtimeId);

    @Modifying
    @Query(value = "UPDATE Bookings SET Status = 'CANCELLED' WHERE UserId = :userId AND Status = 'PENDING' AND CreatedAt < DATE_SUB(NOW(), INTERVAL 10 MINUTE)", nativeQuery = true)
    int cancelExpiredPendingByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query(value = "DELETE b FROM Bookings b JOIN Showtimes s ON s.Id = b.ShowtimeId WHERE s.EndTime IS NOT NULL AND s.EndTime < NOW()", nativeQuery = true)
    int deleteBookingsForEndedShowtimes();

        @Query(value = "SELECT YEARWEEK(b.CreatedAt, 1) AS yearWeek, " +
            "DATE_SUB(DATE(b.CreatedAt), INTERVAL WEEKDAY(b.CreatedAt) DAY) AS weekStart, " +
            "SUM(b.TotalPrice) AS revenue, " +
            "COUNT(*) AS bookingCount " +
            "FROM Bookings b " +
            "JOIN Showtimes s ON s.Id = b.ShowtimeId " +
            "JOIN Rooms r ON r.Id = s.RoomId " +
            "WHERE b.Status = 'PAID' AND b.CreatedAt >= DATE_SUB(CURDATE(), INTERVAL :weeks WEEK) AND r.CinemaId = :cinemaId " +
            "GROUP BY yearWeek, weekStart " +
            "ORDER BY yearWeek",
            nativeQuery = true)
        List<Object[]> aggregateWeeklyRevenueByCinema(@Param("weeks") int weeks, @Param("cinemaId") int cinemaId);

        @Query(value = "SELECT DATE_FORMAT(b.CreatedAt, '%Y-%m') AS month, " +
            "SUM(b.TotalPrice) AS revenue, " +
            "COUNT(*) AS bookingCount " +
            "FROM Bookings b " +
            "JOIN Showtimes s ON s.Id = b.ShowtimeId " +
            "JOIN Rooms r ON r.Id = s.RoomId " +
            "WHERE b.Status = 'PAID' AND b.CreatedAt >= DATE_SUB(CURDATE(), INTERVAL :months MONTH) AND r.CinemaId = :cinemaId " +
            "GROUP BY month " +
            "ORDER BY month",
            nativeQuery = true)
        List<Object[]> aggregateMonthlyRevenueByCinema(@Param("months") int months, @Param("cinemaId") int cinemaId);
}