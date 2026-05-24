package com.example.controller;

import com.example.entity.*;
import com.example.repository.*;
import com.example.service.SepayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private static final long PENDING_HOLD_MINUTES = 10;

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final RoomRepository roomRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final SepayService sepayService;

    public BookingController(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            RoomRepository roomRepository,
            MovieRepository movieRepository,
            UserRepository userRepository,
                SepayService sepayService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.roomRepository = roomRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
        this.sepayService = sepayService;
    }

    @GetMapping("/showtimes/by-cinema-movie")
    @Transactional
    public ResponseEntity<?> getShowtimesByCinemaAndMovie(
            @RequestParam Integer cinemaId,
            @RequestParam Integer movieId
    ) {
        cleanupEndedShowtimeBookings();
        List<Showtime> allShowtimes = showtimeRepository.findAll();
        List<Room> rooms = roomRepository.findByCinemaId(cinemaId);
        Set<Integer> roomIds = rooms.stream().map(Room::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();

        List<Showtime> filtered = allShowtimes.stream()
            .filter(s -> Objects.equals(s.getMovieId(), movieId) && roomIds.contains(s.getRoomId()))
            .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .sorted(Comparator.comparing(Showtime::getStartTime))
                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/showtimes/{showtimeId}/booked-seat-ids")
    @Transactional
    public ResponseEntity<List<Integer>> getBookedSeatIds(@PathVariable Integer showtimeId) {
        cleanupEndedShowtimeBookings();
        bookingRepository.cancelExpiredPendingByShowtimeId(showtimeId);
        return ResponseEntity.ok(bookingSeatRepository.findBookedSeatIdsByShowtimeId(showtimeId));
    }

    @Transactional
    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@RequestBody CreateBookingRequest request) {
        // Keep this endpoint for direct/manual booking creation.
        return createBookingInternal(request, false);
    }

    @Transactional
    @PostMapping("/bookings/payment-url")
    public ResponseEntity<?> createBookingAndPaymentUrl(@RequestBody CreateBookingRequest request) {
        return createBookingInternal(request, true);
    }

    @GetMapping("/payments/sepay/success")
    @Transactional
    public ResponseEntity<Void> handleSepaySuccess(@RequestParam(required = false) Integer bookingId) {
        PaymentFinalizeResult result = finalizeBookingAfterPayment(bookingId);
        if (result.success) {
            return redirectToFrontend("SUCCESS", result.message, bookingId);
        }
        return redirectToFrontend("FAILED", result.message, bookingId);
    }

    @GetMapping("/payments/sepay/error")
    @Transactional
    public ResponseEntity<Void> handleSepayError(@RequestParam(required = false) Integer bookingId) {
        if (bookingId != null) {
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isPresent()) {
                Booking booking = bookingOpt.get();
                if (booking.getStatus() == Booking.BookingStatus.PAID) {
                    return redirectToFrontend("SUCCESS", "Don_da_thanh_toan", bookingId);
                }
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
            }
        }
        return redirectToFrontend("FAILED", "Thanh_toan_that_bai", bookingId);
    }

    @GetMapping("/payments/sepay/cancel")
    @Transactional
    public ResponseEntity<Void> handleSepayCancel(@RequestParam(required = false) Integer bookingId) {
        if (bookingId != null) {
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isPresent()) {
                Booking booking = bookingOpt.get();
                if (booking.getStatus() == Booking.BookingStatus.PAID) {
                    return redirectToFrontend("SUCCESS", "Don_da_thanh_toan", bookingId);
                }
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
            }
        }
        return redirectToFrontend("CANCELLED", "Ban_da_huy_thanh_toan", bookingId);
    }

    @PostMapping("/payments/sepay/ipn")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleSepayIpn(
            @RequestHeader(value = "X-Secret-Key", required = false) String secret,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "secret_key", required = false) String secretKeyParam,
            @RequestBody Map<String, Object> payload
    ) {
        String providedSecret = resolveWebhookSecret(secret, authorization, secretKeyParam, payload);
        String expectedSecret = normalizeSecret(sepayService.getSecretKey());
        if (providedSecret == null || expectedSecret == null || !providedSecret.equals(expectedSecret)) {
            logger.warn("Reject SePay IPN: invalid secret. hasHeaderXSecret={}, hasAuthorization={}, hasQuerySecretKey={}",
                    secret != null,
                    authorization != null,
                    secretKeyParam != null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String notificationType = String.valueOf(payload.getOrDefault("notification_type", ""));
        String normalizedType = notificationType == null ? "" : notificationType.trim().toUpperCase();
        boolean isPaidSignal = normalizedType.isBlank()
                || normalizedType.contains("PAID")
                || normalizedType.contains("TRANSFER");
        if (!isPaidSignal) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Ignored"));
        }

        Integer bookingId = sepayService.parseBookingIdFromIpnPayload(payload);
        if (bookingId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cannot resolve bookingId from payload"));
        }

        PaymentFinalizeResult result = finalizeBookingAfterPayment(bookingId);
        if ("NOT_FOUND".equals(result.code)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", result.message));
        }

        return ResponseEntity.ok(Map.of("success", result.success, "message", result.message));
    }

    private String resolveWebhookSecret(
            String headerSecret,
            String authorization,
            String querySecret,
            Map<String, Object> payload
    ) {
        String fromHeader = normalizeSecret(headerSecret);
        if (fromHeader != null) {
            return fromHeader;
        }

        String fromQuery = normalizeSecret(querySecret);
        if (fromQuery != null) {
            return fromQuery;
        }

        if (authorization != null) {
            String normalizedAuth = authorization.trim();
            String lowered = normalizedAuth.toLowerCase();

            if (lowered.startsWith("bearer ")) {
                String token = normalizeSecret(normalizedAuth.substring(7));
                if (token != null) {
                    return token;
                }
            }

            if (lowered.startsWith("basic ")) {
                String basicToken = normalizeSecret(normalizedAuth.substring(6));
                if (basicToken != null) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(basicToken);
                        String decodedText = new String(decoded, StandardCharsets.UTF_8);
                        int idx = decodedText.indexOf(':');
                        if (idx >= 0 && idx < decodedText.length() - 1) {
                            String passPart = normalizeSecret(decodedText.substring(idx + 1));
                            if (passPart != null) {
                                return passPart;
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid base64 and continue fallback parsing.
                    }
                }
            }

            int firstSpace = normalizedAuth.indexOf(' ');
            if (firstSpace > 0 && firstSpace < normalizedAuth.length() - 1) {
                String authValue = normalizeSecret(normalizedAuth.substring(firstSpace + 1));
                if (authValue != null) {
                    return authValue;
                }
            }

            String plainAuth = normalizeSecret(normalizedAuth);
            if (plainAuth != null) {
                return plainAuth;
            }
        }

        if (payload != null) {
            String[] keys = {"secret_key", "secretKey", "webhook_secret", "signature"};
            for (String key : keys) {
                Object raw = payload.get(key);
                String normalized = normalizeSecret(raw == null ? null : String.valueOf(raw));
                if (normalized != null) {
                    return normalized;
                }
            }
        }

        return null;
    }

    private String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @GetMapping("/bookings/{bookingId}/payment-info")
    @Transactional
    public ResponseEntity<?> getPaymentInfo(@PathVariable Integer bookingId) {
        if (bookingId == null) {
            return ResponseEntity.badRequest().body("Thiếu bookingId.");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy đơn đặt vé.");
        }

        Booking booking = cancelIfExpiredPending(bookingOpt.get());
        LocalDateTime expiresAt = getBookingExpiresAt(booking);
        long remainingSeconds = calculateRemainingSeconds(booking);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bookingId", booking.getId());
        response.put("status", booking.getStatus());
        response.put("totalPrice", booking.getTotalPrice());
        response.put("createdAt", booking.getCreatedAt());
        response.put("expiresAt", expiresAt);
        response.put("remainingSeconds", remainingSeconds);
        response.put("canPay", booking.getStatus() == Booking.BookingStatus.PENDING && remainingSeconds > 0);
        response.put("bank", sepayService.getQrBank());
        response.put("account", sepayService.getQrAccount());
        response.put("transferContent", sepayService.buildTransferDescription(booking.getId()));
        response.put("qrImageUrl", sepayService.buildQrImageUrl(booking.getId(), booking.getTotalPrice()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/{bookingId}/status")
    @Transactional
    public ResponseEntity<?> getBookingStatus(@PathVariable Integer bookingId) {
        if (bookingId == null) {
            return ResponseEntity.badRequest().body("Thiếu bookingId.");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy đơn đặt vé.");
        }

        Booking booking = cancelIfExpiredPending(bookingOpt.get());
        long remainingSeconds = calculateRemainingSeconds(booking);

        return ResponseEntity.ok(Map.of(
                "bookingId", booking.getId(),
                "status", booking.getStatus(),
            "isPaid", booking.getStatus() == Booking.BookingStatus.PAID,
            "remainingSeconds", remainingSeconds,
            "canPay", booking.getStatus() == Booking.BookingStatus.PENDING && remainingSeconds > 0
        ));
    }

    private PaymentFinalizeResult finalizeBookingAfterPayment(Integer bookingId) {
        if (bookingId == null) {
            return new PaymentFinalizeResult(false, "INVALID", "Ma_don_hang_khong_hop_le");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return new PaymentFinalizeResult(false, "NOT_FOUND", "Khong_tim_thay_don_dat_ve");
        }

        Booking booking = bookingOpt.get();

        if (booking.getStatus() == Booking.BookingStatus.PAID) {
            return new PaymentFinalizeResult(true, "ALREADY_PAID", "Don_da_thanh_toan");
        }

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            return new PaymentFinalizeResult(false, "CANCELLED", "Don_da_bi_huy");
        }

        if (isBookingExpired(booking)) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            return new PaymentFinalizeResult(false, "EXPIRED", "Don_dat_ve_da_het_han_10_phut");
        }

        List<Integer> seatIds = bookingSeatRepository.findByBookingId(booking.getId()).stream()
                .map(BookingSeat::getSeatId)
                .filter(Objects::nonNull)
                .toList();

        if (!seatIds.isEmpty()) {
            long conflictCount = bookingSeatRepository.countPaidSeatConflicts(
                    booking.getShowtimeId(),
                    booking.getId(),
                    seatIds
            );
            if (conflictCount > 0) {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                return new PaymentFinalizeResult(false, "SEAT_CONFLICT", "Ghe_da_duoc_thanh_toan_boi_don_khac");
            }
        }

        booking.setStatus(Booking.BookingStatus.PAID);
        bookingRepository.save(booking);
        return new PaymentFinalizeResult(true, "PAID", "Thanh_toan_thanh_cong");
    }

    private boolean isBookingExpired(Booking booking) {
        LocalDateTime createdAt = booking.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return createdAt.plusMinutes(PENDING_HOLD_MINUTES).isBefore(LocalDateTime.now());
    }

    private Booking cancelIfExpiredPending(Booking booking) {
        if (booking.getStatus() == Booking.BookingStatus.PENDING && isBookingExpired(booking)) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            return bookingRepository.save(booking);
        }
        return booking;
    }

    private LocalDateTime getBookingExpiresAt(Booking booking) {
        LocalDateTime createdAt = booking.getCreatedAt();
        if (createdAt == null) {
            return null;
        }
        return createdAt.plusMinutes(PENDING_HOLD_MINUTES);
    }

    private long calculateRemainingSeconds(Booking booking) {
        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            return 0;
        }

        LocalDateTime expiresAt = getBookingExpiresAt(booking);
        if (expiresAt == null) {
            return 0;
        }

        long seconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0, seconds);
    }

    private static class PaymentFinalizeResult {
        private final boolean success;
        private final String code;
        private final String message;

        private PaymentFinalizeResult(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }
    }

    private ResponseEntity<?> createBookingInternal(CreateBookingRequest request, boolean requirePaymentUrl) {
        if (request == null || request.getUserId() == null || request.getShowtimeId() == null || request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            return ResponseEntity.badRequest().body("Thiếu thông tin đặt vé.");
        }

        cleanupEndedShowtimeBookings();
        bookingRepository.cancelExpiredPendingByShowtimeId(request.getShowtimeId());

        if (userRepository.findById(request.getUserId()).isEmpty()) {
            return ResponseEntity.badRequest().body("Người dùng không tồn tại.");
        }

        Optional<Showtime> showtimeOpt = showtimeRepository.findById(request.getShowtimeId());
        if (showtimeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Suất chiếu không tồn tại.");
        }

        Showtime showtime = showtimeOpt.get();
        LocalDateTime startTime = showtime.getStartTime();
        if (startTime == null || !startTime.isAfter(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Suất chiếu đã bắt đầu hoặc không hợp lệ.");
        }
        Integer roomId = showtime.getRoomId();

        List<Seat> seats = seatRepository.findAllById(request.getSeatIds());
        if (seats.size() != request.getSeatIds().size()) {
            return ResponseEntity.badRequest().body("Có ghế không tồn tại trong hệ thống.");
        }

        boolean invalidRoomSeat = seats.stream().anyMatch(s -> !Objects.equals(s.getRoomId(), roomId));
        if (invalidRoomSeat) {
            return ResponseEntity.badRequest().body("Có ghế không thuộc phòng của suất chiếu.");
        }

        List<Integer> bookedSeatIds = bookingSeatRepository.findBookedSeatIdsByShowtimeId(request.getShowtimeId());
        Set<Integer> bookedSet = new HashSet<>(bookedSeatIds);
        List<Integer> duplicate = request.getSeatIds().stream().filter(bookedSet::contains).toList();
        if (!duplicate.isEmpty()) {
            return ResponseEntity.badRequest().body("Một số ghế đã được đặt: " + duplicate);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<BookingSeat> bookingSeatRows = new ArrayList<>();

        for (Seat seat : seats) {
            BigDecimal seatPrice = calculateSeatPrice(showtime.getBasePrice(), seat.getType());
            total = total.add(seatPrice);

            BookingSeat item = new BookingSeat();
            item.setSeatId(seat.getId());
            item.setPrice(seatPrice);
            bookingSeatRows.add(item);
        }

        Booking booking = new Booking();
        booking.setUserId(request.getUserId());
        booking.setShowtimeId(request.getShowtimeId());
        booking.setTotalPrice(total);
        booking.setStatus(request.getStatus() == null ? Booking.BookingStatus.PENDING : request.getStatus());
        booking = bookingRepository.save(booking);

        Integer bookingId = booking.getId();
        for (BookingSeat item : bookingSeatRows) {
            item.setBookingId(bookingId);
        }
        bookingSeatRepository.saveAll(bookingSeatRows);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bookingId", bookingId);
        response.put("status", booking.getStatus());
        response.put("totalPrice", total);
        response.put("seatCount", bookingSeatRows.size());
        response.put("createdAt", LocalDateTime.now());

        if (requirePaymentUrl) {
            if (!sepayService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Thanh toán SePay chưa được cấu hình. Vui lòng cấu hình payment.sepay.merchant-id và payment.sepay.secret-key.");
            }

            response.put("paymentMethod", "SEPAY_QR");
            response.put("bank", sepayService.getQrBank());
            response.put("account", sepayService.getQrAccount());
            response.put("transferContent", sepayService.buildTransferDescription(bookingId));
            response.put("qrImageUrl", sepayService.buildQrImageUrl(bookingId, total));
        }

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Void> redirectToFrontend(String status, String message, Integer bookingId) {
        StringBuilder url = new StringBuilder(sepayService.getFrontendResultUrl())
                .append("?status=")
                .append(urlEncode(status))
                .append("&message=")
                .append(urlEncode(message));

        if (bookingId != null) {
            url.append("&bookingId=").append(bookingId);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url.toString())
                .build();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void cleanupEndedShowtimeBookings() {
        bookingRepository.deleteBookingsForEndedShowtimes();
    }

    @GetMapping("/admin/bookings")
    public ResponseEntity<List<Map<String, Object>>> getBookingsForAdmin(@RequestParam Integer cinemaId) {
        List<Booking> bookings = bookingRepository.findByCinemaId(cinemaId);

        return ResponseEntity.ok(mapBookingRows(bookings));
    }

    @GetMapping("/admin/revenue/weekly")
    public ResponseEntity<List<Map<String, Object>>> getWeeklyRevenue(
            @RequestParam(defaultValue = "12") int weeks
    ) {
        int safeWeeks = Math.max(1, Math.min(52, weeks));
        List<Object[]> rows = bookingRepository.aggregateWeeklyRevenue(safeWeeks);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("yearWeek", row[0]);
            item.put("weekStart", row[1]);
            item.put("revenue", row[2]);
            item.put("bookingCount", row[3]);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/revenue/monthly")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue(
            @RequestParam(defaultValue = "12") int months
    ) {
        int safeMonths = Math.max(1, Math.min(36, months));
        List<Object[]> rows = bookingRepository.aggregateMonthlyRevenue(safeMonths);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", row[0]);
            item.put("revenue", row[1]);
            item.put("bookingCount", row[2]);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/top-movies")
    public ResponseEntity<List<Map<String, Object>>> getTopMovies(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;
        try {
            if (from != null && !from.isBlank()) {
                fromDate = LocalDate.parse(from).atStartOfDay();
            }
            if (to != null && !to.isBlank()) {
                toDate = LocalDate.parse(to).plusDays(1).atStartOfDay();
            }
        } catch (Exception ignored) {
            fromDate = null;
            toDate = null;
        }

        List<Object[]> rows = bookingSeatRepository.aggregateTopMoviesByVenue(fromDate, toDate);
        List<Map<String, Object>> result = new ArrayList<>();

        int count = 0;
        for (Object[] row : rows) {
            if (count >= safeLimit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("movieId", row[0]);
            item.put("movieTitle", row[1]);
            item.put("cinemaId", row[2]);
            item.put("cinemaName", row[3]);
            item.put("roomId", row[4]);
            item.put("roomName", row[5]);
            item.put("seatCount", row[6]);
            item.put("bookingCount", row[7]);
            result.add(item);
            count++;
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/bookings/history")
    @Transactional
    public ResponseEntity<?> getBookingHistoryByUser(@RequestParam Integer userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body("Thiếu userId.");
        }

        if (userRepository.findById(userId).isEmpty()) {
            return ResponseEntity.badRequest().body("Người dùng không tồn tại.");
        }

        bookingRepository.cancelExpiredPendingByUserId(userId);

        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(mapBookingRows(bookings));
    }

    private List<Map<String, Object>> mapBookingRows(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return new ArrayList<>();
        }

        List<Movie> movies = movieRepository.findAll();
        Map<Integer, String> movieTitles = movies.stream()
                .collect(Collectors.toMap(Movie::getId, Movie::getTitle));

        List<User> users = userRepository.findAll();
        Map<Integer, String> usernames = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        Map<Integer, Showtime> showtimeMap = showtimeRepository.findAll().stream()
                .collect(Collectors.toMap(Showtime::getId, s -> s));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking booking : bookings) {
            Showtime showtime = showtimeMap.get(booking.getShowtimeId());
            if (showtime == null) {
                continue;
            }

            List<BookingSeat> items = bookingSeatRepository.findByBookingId(booking.getId());
            List<Integer> seatIds = items.stream().map(BookingSeat::getSeatId).toList();
            Map<Integer, Seat> seatMap = seatRepository.findAllById(seatIds).stream()
                    .collect(Collectors.toMap(Seat::getId, s -> s));

            Map<String, Integer> seatTypeCounts = new LinkedHashMap<>();
            for (BookingSeat item : items) {
                Seat seat = seatMap.get(item.getSeatId());
                String type = seat == null || seat.getType() == null ? "UNKNOWN" : seat.getType().toUpperCase();
                seatTypeCounts.put(type, seatTypeCounts.getOrDefault(type, 0) + 1);
            }

            String seatTypeSummary = seatTypeCounts.entrySet().stream()
                    .map(e -> e.getKey() + " x" + e.getValue())
                    .collect(Collectors.joining(", "));

                String seatLabels = items.stream()
                    .map(item -> seatMap.get(item.getSeatId()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                        .comparing((Seat s) -> {
                        String row = s.getSeatRow();
                        return row == null ? "" : row.toUpperCase();
                        })
                        .thenComparing(s -> s.getSeatNumber() == null ? Integer.MAX_VALUE : s.getSeatNumber()))
                    .map(this::formatSeatLabel)
                    .collect(Collectors.joining(", "));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", booking.getId());
            row.put("userId", booking.getUserId());
            row.put("username", usernames.getOrDefault(booking.getUserId(), "N/A"));
            row.put("showtimeId", booking.getShowtimeId());
            row.put("movieId", showtime.getMovieId());
            row.put("movieTitle", movieTitles.getOrDefault(showtime.getMovieId(), "N/A"));
            row.put("roomId", showtime.getRoomId());
            row.put("startTime", showtime.getStartTime());
            row.put("status", booking.getStatus());
            row.put("totalPrice", booking.getTotalPrice());
            row.put("createdAt", booking.getCreatedAt());
            row.put("seatCount", items.size());
            row.put("seatLabels", seatLabels.isBlank() ? "N/A" : seatLabels);
            row.put("seatTypes", seatTypeSummary.isBlank() ? "N/A" : seatTypeSummary);
            result.add(row);
        }

        return result;
    }

    private String formatSeatLabel(Seat seat) {
        String row = seat.getSeatRow() == null ? "?" : seat.getSeatRow().toUpperCase();
        String number = seat.getSeatNumber() == null ? "?" : String.valueOf(seat.getSeatNumber());
        return row + number;
    }

    private BigDecimal calculateSeatPrice(Double basePrice, String seatType) {
        BigDecimal base = BigDecimal.valueOf(basePrice == null ? 0 : basePrice);
        String normalizedType = seatType == null ? "NORMAL" : seatType.toUpperCase();

        BigDecimal multiplier;
        switch (normalizedType) {
            case "VIP" -> multiplier = BigDecimal.valueOf(1.5);
            case "COUPLE" -> multiplier = BigDecimal.valueOf(2.0);
            default -> multiplier = BigDecimal.ONE;
        }

        return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public static class CreateBookingRequest {
        private Integer userId;
        private Integer showtimeId;
        private List<Integer> seatIds;
        private Booking.BookingStatus status;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public Integer getShowtimeId() {
            return showtimeId;
        }

        public void setShowtimeId(Integer showtimeId) {
            this.showtimeId = showtimeId;
        }

        public List<Integer> getSeatIds() {
            return seatIds;
        }

        public void setSeatIds(List<Integer> seatIds) {
            this.seatIds = seatIds;
        }

        public Booking.BookingStatus getStatus() {
            return status;
        }

        public void setStatus(Booking.BookingStatus status) {
            this.status = status;
        }
    }
}
