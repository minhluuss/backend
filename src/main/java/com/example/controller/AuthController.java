package com.example.controller;

import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String jwtCookieName;
    private final long jwtExpirationMs;
    private final boolean jwtCookieSecure;
    private final String jwtCookieSameSite;

    public AuthController(
            UserRepository repo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${jwt.cookie-name:auth_token}") String jwtCookieName,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs,
            @Value("${jwt.cookie-secure:false}") boolean jwtCookieSecure,
            @Value("${jwt.cookie-samesite:Lax}") String jwtCookieSameSite
    ) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtCookieName = jwtCookieName;
        this.jwtExpirationMs = jwtExpirationMs;
        this.jwtCookieSecure = jwtCookieSecure;
        this.jwtCookieSameSite = jwtCookieSameSite;
    }

    // 🔹 Đăng ký
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {

        // ❌ Check username
        if (repo.existsByUsername(user.getUsername())) {
            return ResponseEntity.badRequest().body("Username đã tồn tại");
        }

        // ❌ Check email trùng
        if (repo.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body("Email đã tồn tại");
        }

        // ❌ Check email format
        if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body("Email không hợp lệ");
        }

        // ✅ OK
        user.setRole(User.Role.USER);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        repo.save(user);

        return ResponseEntity.ok("Đăng ký thành công");
    }

    // 🔹 Đăng nhập
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {

        Optional<User> u = repo.findByUsername(user.getUsername());

        if (u.isPresent() && passwordEncoder.matches(user.getPassword(), u.get().getPassword())) {
            User loggedInUser = u.get();
            loggedInUser.setPassword(null); // ✅ Xóa trắng password trước khi gửi về React để bảo mật
            String token = jwtService.generateToken(loggedInUser);
            ResponseCookie cookie = ResponseCookie.from(jwtCookieName, token)
                .httpOnly(true)
                .secure(jwtCookieSecure)
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpirationMs))
                .sameSite(jwtCookieSameSite)
                .build();

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(loggedInUser);
        }

        return ResponseEntity
                .status(401)
                .body("Sai tài khoản hoặc mật khẩu");
    }

    // 🔹 Lấy thông tin user từ JWT cookie
    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(name = "${jwt.cookie-name:auth_token}", required = false) String token) {
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        String username = jwtService.extractUsername(token);
        Optional<User> u = repo.findByUsername(username);
        if (u.isEmpty()) {
            return ResponseEntity.status(401).body("User không tồn tại");
        }

        User loggedInUser = u.get();
        loggedInUser.setPassword(null);
        return ResponseEntity.ok(loggedInUser);
    }

    // 🔹 Đăng xuất
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = ResponseCookie.from(jwtCookieName, "")
                .httpOnly(true)
                .secure(jwtCookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(jwtCookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Đăng xuất thành công");
    }

    // 🔹 Đổi mật khẩu (yêu cầu đăng nhập)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @CookieValue(name = "${jwt.cookie-name:auth_token}", required = false) String token,
            @RequestBody ChangePasswordRequest request
    ) {
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        if (request == null
                || request.oldPassword == null
                || request.newPassword == null
                || request.newPassword.isBlank()) {
            return ResponseEntity.badRequest().body("Thiếu thông tin mật khẩu");
        }

        String username = jwtService.extractUsername(token);
        Optional<User> u = repo.findByUsername(username);
        if (u.isEmpty()) {
            return ResponseEntity.status(401).body("User không tồn tại");
        }

        User user = u.get();
        if (!passwordEncoder.matches(request.oldPassword, user.getPassword())) {
            return ResponseEntity.status(400).body("Mật khẩu cũ không đúng");
        }

        if (request.newPassword.equals(request.oldPassword)) {
            return ResponseEntity.status(400).body("Mật khẩu mới không được trùng mật khẩu cũ");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword));
        repo.save(user);
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    public static class ChangePasswordRequest {
        public String oldPassword;
        public String newPassword;
    }
}