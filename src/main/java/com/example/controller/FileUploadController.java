package com.example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    // Nơi lưu file (Tương ứng với thư mục vừa tạo ở Bước 1)
    private static final String UPLOAD_DIR = "uploads/";

    @PostMapping
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Kiểm tra xem thư mục đã tồn tại chưa, chưa thì tạo mới
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            // Đổi tên file để tránh bị trùng lặp (dùng UUID)
            String originalFileName = file.getOriginalFilename();
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String newFileName = UUID.randomUUID().toString() + fileExtension;

            // Lưu file vào thư mục
            Path path = Paths.get(UPLOAD_DIR + newFileName);
            Files.write(path, file.getBytes());

            // Trả về đường link tương đối để dùng được cả local lẫn deploy
            String fileUrl = "/uploads/" + newFileName;
            return ResponseEntity.ok(fileUrl);

        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi khi tải ảnh lên: " + e.getMessage());
        }
    }
}