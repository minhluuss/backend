package com.example.controller;

import java.util.List;
import com.example.entity.Showtime;
import com.example.repository.BookingRepository;
import com.example.repository.ShowtimeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private final ShowtimeRepository repo;
    private final BookingRepository bookingRepository;

    public ShowtimeController(ShowtimeRepository repo, BookingRepository bookingRepository) {
        this.repo = repo;
        this.bookingRepository = bookingRepository;
    }

    // API lưu suất chiếu mới
    @PostMapping
    public ResponseEntity<?> addShowtime(@RequestBody Showtime showtime) {
        if (showtime == null
                || showtime.getRoomId() == null
                || showtime.getStartTime() == null
                || showtime.getEndTime() == null) {
            return ResponseEntity.badRequest().body("Thieu_thong_tin_suat_chieu");
        }

        if (!showtime.getEndTime().isAfter(showtime.getStartTime())) {
            return ResponseEntity.badRequest().body("Thoi_gian_khong_hop_le");
        }

        long conflicts = repo.countOverlappingShowtimes(
                showtime.getRoomId(),
                showtime.getStartTime(),
                showtime.getEndTime());
        
        if (conflicts > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Trùng suất chiếu ");
        }

        return ResponseEntity.ok(repo.save(showtime));
    }

    // Lấy toàn bộ suất chiếu
    @GetMapping
    public List<Showtime> getAllShowtimes() {
        return repo.findAll();
    }

    // Cập nhật suất chiếu
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable Integer id, @RequestBody Showtime details) {
        if (details == null
                || details.getRoomId() == null
                || details.getStartTime() == null
                || details.getEndTime() == null) {
            return ResponseEntity.badRequest().body("Thieu_thong_tin_suat_chieu");
        }

        if (!details.getEndTime().isAfter(details.getStartTime())) {
            return ResponseEntity.badRequest().body("Thoi_gian_khong_hop_le");
        }

        long conflicts = repo.countOverlappingShowtimesExcludingId(
                id,
                details.getRoomId(),
                details.getStartTime(),
                details.getEndTime());
        
        if (conflicts > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Trung_suat_chieu_cung_phong");
        }

        return repo.findById(id).map(st -> {
            st.setMovieId(details.getMovieId());
            st.setRoomId(details.getRoomId());
            st.setStartTime(details.getStartTime());
            st.setEndTime(details.getEndTime());
            st.setBasePrice(details.getBasePrice());
            return ResponseEntity.ok((Object) repo.save(st));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Khong_tim_thay_suat_chieu"));
    }

    // Xóa suất chiếu
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowtime(@PathVariable Integer id) {
        if (bookingRepository.existsByShowtimeId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("không thể xóa suất chiếu đã có vé đặt");
        }
        repo.deleteById(id);
        return ResponseEntity.ok().build();
    }
}