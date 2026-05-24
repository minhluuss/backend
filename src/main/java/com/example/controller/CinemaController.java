package com.example.controller;

import com.example.entity.Cinema;
import com.example.repository.CinemaRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cinemas")
public class CinemaController {
    private final CinemaRepository repo;

    public CinemaController(CinemaRepository repo) {
        this.repo = repo;
    }

    // API lấy danh sách rạp (đã dùng ở trang Thêm Suất Chiếu)
    @GetMapping
    public List<Cinema> getAll() {
        return repo.findAll();
    }

    // Lấy thông tin chi tiết của 1 rạp theo ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getCinemaById(@PathVariable Integer id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🎯 API mới: Lưu rạp chiếu phim vào Database
    @PostMapping
    public Cinema addCinema(@RequestBody Cinema cinema) {
        return repo.save(cinema);
    }

    @PutMapping("/{id}")
    public Cinema updateCinema(@PathVariable Integer id, @RequestBody Cinema cinemaDetails) {
        return repo.findById(id).map(cinema -> {
            cinema.setName(cinemaDetails.getName());
            cinema.setLocation(cinemaDetails.getLocation());
            return repo.save(cinema); // Lưu đè thông tin mới
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy rạp chiếu này!"));
    }

    @DeleteMapping("/{id}")
    public void deleteCinema(@PathVariable Integer id) {
        repo.deleteById(id);
    }
}