package com.example.controller;

import com.example.entity.User;
import com.example.entity.PendingRegistration;
import com.example.entity.PasswordOtp;
import com.example.repository.UserRepository;
import com.example.repository.PendingRegistrationRepository;
import com.example.repository.PasswordOtpRepository;
import com.example.security.JwtService;
import com.example.service.EmailService;

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
    private final PendingRegistrationRepository pendingRepo;
    private final PasswordOtpRepository passwordOtpRepo; // ✅ Đã thêm
    private final EmailService emailService;
    private final String jwtCookieName;
    private final long jwtExpirationMs;
    private final boolean jwtCookieSecure;
    private final String jwtCookieSameSite;
    private final long otpExpirationMinutes;

    public AuthController(
            UserRepository repo,
            PendingRegistrationRepository pendingRepo,
            PasswordOtpRepository passwordOtpRepo, // ✅ Đã thêm
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${jwt.cookie-name:auth_token}") String jwtCookieName,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs,
            @Value("${otp.expiration-minutes:10}") long otpExpirationMinutes,
            @Value("${jwt.cookie-secure:false}") boolean jwtCookieSecure,
            @Value("${jwt.cookie-samesite:Lax}") String jwtCookieSameSite) {
        this.repo = repo;
        this.pendingRepo = pendingRepo;
        this.passwordOtpRepo = passwordOtpRepo; // ✅ Đã thêm
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtCookieName = jwtCookieName;
        this.jwtExpirationMs = jwtExpirationMs;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.jwtCookieSecure = jwtCookieSecure;
        this.jwtCookieSameSite = jwtCookieSameSite;
    }

    // 🔹 Đăng ký
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        // Xóa các bản ghi Pending đã hết hạn trước khi xử lý đăng ký mới
        pendingRepo.deleteByExpiresAtBefore(java.time.Instant.now());
        
        // 1. Kiểm tra trong bảng User chính thức
        if (repo.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body("Email đã được sử dụng");
        }
        if (repo.existsByUsername(user.getUsername())) {
            return ResponseEntity.badRequest().body("Username đã tồn tại");
        }

        // 2. Validate định dạng email
        if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body("Email không hợp lệ");
        }

        // 3. Tạo mã OTP mới và tính thời gian hết hạn
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(otpExpirationMinutes * 60);

        // 4. Xử lý logic lưu vào Pending
        Optional<PendingRegistration> existingPending = pendingRepo.findByEmail(user.getEmail());
        PendingRegistration p;

        if (existingPending.isPresent()) {
            // Đã có trong phòng chờ (User ấn Quay lại) -> Cập nhật lại mã và thời gian
            p = existingPending.get();
            p.setUsername(user.getUsername());
            p.setEncodedPassword(passwordEncoder.encode(user.getPassword()));
            p.setCode(code);
            p.setExpiresAt(expiresAt);
        } else {
            // Hoàn toàn mới -> Check xem username có ai đang mượn tạm trong phòng chờ không
            if (pendingRepo.findByUsername(user.getUsername()).isPresent()) {
                return ResponseEntity.badRequest().body("Username đang chờ người khác xác thực");
            }

            // Tạo mới
            p = new PendingRegistration();
            p.setUsername(user.getUsername());
            p.setEmail(user.getEmail());
            p.setEncodedPassword(passwordEncoder.encode(user.getPassword()));
            p.setRole(User.Role.USER.name());
            p.setCode(code);
            p.setExpiresAt(expiresAt);
        }

        // 5. Lưu xuống DB và gửi Email
        pendingRepo.save(p);
        emailService.sendOtp(user.getEmail(), code);

        return ResponseEntity.ok("Mã OTP đã được gửi đến email của bạn");
    }

    @PostMapping("/register/verify")
    public ResponseEntity<?> verifyRegistration(@RequestBody VerifyRequest req) {
        if (req == null || req.email == null || req.code == null) {
            return ResponseEntity.badRequest().body("Thiếu email hoặc mã");
        }

        var opt = pendingRepo.findByEmail(req.email);
        if (opt.isEmpty()) {
            return ResponseEntity.status(400).body("Không tìm thấy đăng ký đang chờ");
        }

        PendingRegistration p = opt.get();
        if (p.getExpiresAt().isBefore(java.time.Instant.now())) {
            pendingRepo.delete(p);
            return ResponseEntity.status(400).body("Mã đã hết hạn");
        }

        if (!p.getCode().equals(req.code)) {
            return ResponseEntity.status(400).body("Mã không hợp lệ");
        }

        User user = new User();
        user.setUsername(p.getUsername());
        user.setEmail(p.getEmail());
        user.setPassword(p.getEncodedPassword());
        user.setRole(User.Role.valueOf(p.getRole()));
        repo.save(user);
        pendingRepo.delete(p);

        return ResponseEntity.ok("Đăng ký thành công");
    }

    public static class VerifyRequest {
        public String email;
        public String code;
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

    // ✅ ĐÃ THÊM: Yêu cầu gửi OTP để đổi mật khẩu
    @PostMapping("/change-password/request-otp")
    public ResponseEntity<?> requestChangePasswordOtp(
            @CookieValue(name = "${jwt.cookie-name:auth_token}", required = false) String token) {
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        String username = jwtService.extractUsername(token);
        Optional<User> u = repo.findByUsername(username);
        if (u.isEmpty()) {
            return ResponseEntity.status(401).body("User không tồn tại");
        }
        User user = u.get();

        // Tiện tay dọn rác OTP cũ
        passwordOtpRepo.deleteByExpiresAtBefore(java.time.Instant.now());

        // Tạo mã OTP và thời gian hết hạn
        String code = String.format("%06d", (int)(Math.random() * 1_000_000));
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(otpExpirationMinutes * 60);

        // Lưu vào DB (Ghi đè nếu đã có mã cũ)
        Optional<PasswordOtp> existing = passwordOtpRepo.findByEmail(user.getEmail());
        PasswordOtp pOtp = existing.orElse(new PasswordOtp());
        pOtp.setEmail(user.getEmail());
        pOtp.setCode(code);
        pOtp.setExpiresAt(expiresAt);
        
        passwordOtpRepo.save(pOtp);

        // Gửi email
        emailService.sendOtp(user.getEmail(), code);

        // Trả về email dưới dạng ẩn một phần để FE dễ hiển thị
        String maskedEmail = user.getEmail().replaceAll("(^[^@]{3}|(?!^)\\G)[^@]", "$1*");
        return ResponseEntity.ok("Mã xác nhận đã được gửi đến email: " + maskedEmail);
    }

    // ✅ ĐÃ SỬA: Xác nhận OTP và Đổi mật khẩu
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @CookieValue(name = "${jwt.cookie-name:auth_token}", required = false) String token,
            @RequestBody ChangePasswordRequest request) {
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        if (request == null || request.oldPassword == null || request.newPassword == null || request.otp == null) {
            return ResponseEntity.badRequest().body("Vui lòng nhập đầy đủ thông tin và mã xác nhận");
        }

        String username = jwtService.extractUsername(token);
        Optional<User> u = repo.findByUsername(username);
        if (u.isEmpty()) {
            return ResponseEntity.status(401).body("User không tồn tại");
        }

        User user = u.get();
        
        // 1. Check mật khẩu cũ
        if (!passwordEncoder.matches(request.oldPassword, user.getPassword())) {
            return ResponseEntity.status(400).body("Mật khẩu cũ không đúng");
        }
        if (request.newPassword.equals(request.oldPassword)) {
            return ResponseEntity.status(400).body("Mật khẩu mới không được trùng mật khẩu cũ");
        }

        // 2. Check mã OTP
        Optional<PasswordOtp> optOtp = passwordOtpRepo.findByEmail(user.getEmail());
        if (optOtp.isEmpty()) {
            return ResponseEntity.status(400).body("Bạn chưa yêu cầu mã OTP");
        }

        PasswordOtp pOtp = optOtp.get();
        if (pOtp.getExpiresAt().isBefore(java.time.Instant.now())) {
            passwordOtpRepo.delete(pOtp); // Quá hạn thì tự xóa
            return ResponseEntity.status(400).body("Mã xác nhận đã hết hạn");
        }

        if (!pOtp.getCode().equals(request.otp)) {
            return ResponseEntity.status(400).body("Mã xác nhận không chính xác");
        }

        // 3. OTP đúng -> Tiến hành đổi mật khẩu
        user.setPassword(passwordEncoder.encode(request.newPassword));
        repo.save(user);
        
        // Dùng xong thì xóa mã OTP đi để tránh dùng lại
        passwordOtpRepo.delete(pOtp); 

        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    public static class ChangePasswordRequest {
        public String oldPassword;
        public String newPassword;
        public String otp; // ✅ Đã thêm trường otp
    }
}