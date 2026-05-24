package com.example.repository;

import com.example.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; 
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {
    
    // 1. Lấy danh sách ghế của phòng (Đã có sắp xếp để React render đúng)
    List<Seat> findByRoomIdOrderBySeatNumberAsc(Integer roomId);
    
    // 2. Kiểm tra phòng đã có ghế chưa
    boolean existsByRoomId(Integer roomId);

    // 3. Xóa toàn bộ ghế cũ của một phòng
    @Modifying 
    @Transactional
    void deleteByRoomId(Integer roomId);
}