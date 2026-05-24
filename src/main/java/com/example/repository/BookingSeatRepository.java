package com.example.repository;

import com.example.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Integer> {

    List<BookingSeat> findByBookingId(Integer bookingId);

    @Query(value = "SELECT bs.SeatId FROM BookingSeats bs JOIN Bookings b ON b.Id = bs.BookingId WHERE b.ShowtimeId = :showtimeId AND (b.Status = 'PAID' OR (b.Status = 'PENDING' AND b.CreatedAt >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)))", nativeQuery = true)
    List<Integer> findBookedSeatIdsByShowtimeId(@Param("showtimeId") Integer showtimeId);

    @Query(value = "SELECT COUNT(*) FROM BookingSeats bs JOIN Bookings b ON b.Id = bs.BookingId WHERE b.ShowtimeId = :showtimeId AND b.Status = 'PAID' AND b.Id <> :bookingId AND bs.SeatId IN (:seatIds)", nativeQuery = true)
    long countPaidSeatConflicts(
            @Param("showtimeId") Integer showtimeId,
            @Param("bookingId") Integer bookingId,
            @Param("seatIds") List<Integer> seatIds
    );

            @Query(value = "SELECT m.Id AS movieId, m.Title AS movieTitle, " +
                "c.Id AS cinemaId, c.Name AS cinemaName, " +
                "r.Id AS roomId, r.Name AS roomName, " +
                "COUNT(bs.Id) AS seatCount, COUNT(DISTINCT b.Id) AS bookingCount " +
                "FROM BookingSeats bs " +
                "JOIN Bookings b ON b.Id = bs.BookingId " +
                "JOIN Showtimes s ON s.Id = b.ShowtimeId " +
                "JOIN Rooms r ON r.Id = s.RoomId " +
                "JOIN Cinemas c ON c.Id = r.CinemaId " +
                "JOIN Movies m ON m.Id = s.MovieId " +
                "WHERE b.Status = 'PAID' " +
                "AND (:fromDate IS NULL OR b.CreatedAt >= :fromDate) " +
                "AND (:toDate IS NULL OR b.CreatedAt < :toDate) " +
                "GROUP BY m.Id, m.Title, c.Id, c.Name, r.Id, r.Name " +
                "ORDER BY bookingCount DESC, seatCount DESC",
                nativeQuery = true)
            List<Object[]> aggregateTopMoviesByVenue(
                @Param("fromDate") java.time.LocalDateTime fromDate,
                @Param("toDate") java.time.LocalDateTime toDate
            );
}
