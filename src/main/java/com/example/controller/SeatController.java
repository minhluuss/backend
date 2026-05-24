package com.example.controller;

import com.example.entity.Seat;
import com.example.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SeatController {

    @Autowired
    private SeatRepository seatRepository;

    @GetMapping("/rooms/{roomId}/seats")
    public ResponseEntity<List<Seat>> getSeatsByRoom(@PathVariable Integer roomId) {
        return ResponseEntity.ok(seatRepository.findByRoomIdOrderBySeatNumberAsc(roomId));
    }

    @Transactional
    @PutMapping("/rooms/{roomId}/sync-seats")
    public ResponseEntity<?> syncSeats(@PathVariable Integer roomId, @RequestParam int targetCount) {
        if (targetCount < 0) {
            return ResponseEntity.badRequest().body("Số lượng ghế không được âm!");
        }

        try {
            // 1. XÓA SẠCH ghế cũ để tái cấu trúc
            seatRepository.deleteByRoomId(roomId);

            // 🔥 Ép thực hiện xóa ngay lập tức để tránh lỗi Duplicate Entry khi lưu mới
            seatRepository.flush(); 

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi khi làm sạch sơ đồ: " + e.getMessage());
        }

        if (targetCount == 0) {
            return ResponseEntity.ok("Đã xóa toàn bộ sơ đồ ghế của phòng.");
        }

        // 2. TÍNH TOÁN THÔNG SỐ PHÒNG
        int numRows = (targetCount + 9) / 10;
        List<Seat> newSeats = new ArrayList<>();

        for (int i = 0; i < targetCount; i++) {
            int rowIndex = i / 10;
            int seatNumInRow = (i % 10) + 1;
            char rowChar = (char) ('A' + rowIndex);

            Seat s = new Seat();
            s.setRoomId(roomId);
            s.setSeatRow(String.valueOf(rowChar));
            s.setSeatNumber(seatNumInRow);

            // 3. LOGIC PHÂN LOẠI CHUẨN RẠP PHIM (CGV/Lotte Style)
            if (numRows > 1 && rowIndex == numRows - 1) {
                // HÀNG CUỐI: Luôn là Couple (Sweetbox)
                s.setType("COUPLE");
            } else if (numRows >= 5) {
                // Rạp từ 5 hàng trở lên:
                // - 2 hàng đầu (A, B) là NORMAL (vì quá gần màn hình)
                // - 1 hàng kế cuối là NORMAL (vùng biên phía sau)
                // - Các hàng ở giữa là VIP (Vị trí vàng)
                if (rowIndex >= 2 && rowIndex < numRows - 2) {
                    s.setType("VIP");
                } else {
                    s.setType("NORMAL");
                }
            } else {
                // Rạp nhỏ (< 5 hàng): Mọi thứ là Normal trừ hàng cuối
                s.setType("NORMAL");
            }

            newSeats.add(s);
        }

        // 4. LƯU DỮ LIỆU MỚI
        seatRepository.saveAll(newSeats);

        return ResponseEntity.ok("Đã tái cấu trúc thành công " + targetCount + " ghế (" + numRows + " hàng).");
    }
}