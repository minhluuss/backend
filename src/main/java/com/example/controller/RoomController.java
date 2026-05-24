package com.example.controller;

import com.example.entity.Room;
import com.example.repository.RoomRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomRepository repo;

    public RoomController(RoomRepository repo) {
        this.repo = repo;
    }

    // 1. Lấy toàn bộ phòng
    @GetMapping
    public List<Room> getAllRooms() {
        return repo.findAll();
    }

    // 2. 🎯 Lấy phòng theo Rạp (Sẽ dùng cho trang Chọn Phòng)
    @GetMapping("/cinema/{cinemaId}")
    public List<Room> getRoomsByCinema(@PathVariable Integer cinemaId) {
        return repo.findByCinemaId(cinemaId);
    }

    // 3. THÊM MỚI phòng
    @PostMapping
    public Room addRoom(@RequestBody Room room) {
        return repo.save(room);
    }

    // 4. CẬP NHẬT phòng
    @PutMapping("/{id}")
    public Room updateRoom(@PathVariable Integer id, @RequestBody Room details) {
        return repo.findById(id).map(room -> {
            room.setCinemaId(details.getCinemaId());
            room.setName(details.getName());
            return repo.save(room);
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy phòng!"));
    }

    // 5. XÓA phòng
    @DeleteMapping("/{id}")
    public void deleteRoom(@PathVariable Integer id) {
        repo.deleteById(id);
    }

}