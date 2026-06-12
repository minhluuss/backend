package com.example.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendOtp(String toEmail, String code) {
        // Nếu JavaMailSender được cấu hình, gửi email thật; nếu không, ghi log mã OTP
        if (mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(toEmail);
                msg.setSubject("Your verification code");
                msg.setText("Mã xác thực của bạn: " + code + " (hết hạn trong 10 phút)");
                mailSender.send(msg);
                return;
            } catch (Exception e) {
                logger.warn("Failed sending OTP email, falling back to log", e);
            }
        }

        // Dự phòng: ghi log
        logger.info("OTP for {} = {} (use SMTP to send real email)", toEmail, code);
    }

    // ===============================================
    // 2. HÀM GỬI THÔNG TIN VÉ XEM PHIM (HTML)
    // ===============================================
    public void sendTicketEmail(
            String toEmail,
            String bookingId,
            String movieTitle,
            String cinemaName,
            String roomName, // 👈 THÊM THAM SỐ NÀY
            String showtime,
            String seats,
            String totalPrice) {

        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setTo(toEmail);
                helper.setSubject("🍿 CinemaBooking - Xác nhận đặt vé thành công #" + bookingId);

                // Giao diện HTML của vé (Đã thêm dòng Phòng chiếu)
                String htmlContent = "<div style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 30px;'>"
                        + "  <div style='max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.1);'>"
                        + "    <div style='background: linear-gradient(135deg, #ef4444, #b91c1c); padding: 25px; text-align: center; color: white;'>"
                        + "      <h2 style='margin: 0; font-size: 24px; letter-spacing: 1px;'>VÉ XEM PHIM ĐIỆN TỬ</h2>"
                        + "      <p style='margin: 5px 0 0 0; opacity: 0.8;'>Cảm ơn bạn đã lựa chọn dịch vụ của chúng tôi</p>"
                        + "    </div>"
                        + "    <div style='padding: 25px; color: #1f2937; line-height: 1.6;'>"
                        + "      <h3 style='color: #b91c1c; margin-top: 0; font-size: 20px; border-bottom: 2px dashed #e5e7eb; padding-bottom: 10px;'>"
                        + movieTitle + "</h3>"
                        + "      <table style='width: 100%; border-collapse: collapse; margin-top: 15px;'>"
                        + "        <tr><td style='padding: 6px 0; color: #6b7280; width: 35%;'>Mã đơn vé:</td><td style='padding: 6px 0; font-weight: bold; color: #111827;'>#"
                        + bookingId + "</td></tr>"
                        + "        <tr><td style='padding: 6px 0; color: #6b7280;'>Rạp chiếu:</td><td style='padding: 6px 0; font-weight: bold;'>"
                        + cinemaName + "</td></tr>"
                        + "        <tr><td style='padding: 6px 0; color: #6b7280;'>Phòng chiếu:</td><td style='padding: 6px 0; font-weight: bold; color: #d97706;'>"
                        + roomName + "</td></tr>" // 👈 DÒNG HIỂN THỊ PHÒNG CHIẾU
                        + "        <tr><td style='padding: 6px 0; color: #6b7280;'>Suất chiếu:</td><td style='padding: 6px 0; font-weight: bold; color: #2563eb;'>"
                        + showtime + "</td></tr>"
                        + "        <tr><td style='padding: 6px 0; color: #6b7280;'>Ghế ngồi:</td><td style='padding: 6px 0; font-weight: bold; color: #ef4444;'>"
                        + seats + "</td></tr>"
                        + "        <tr><td style='padding: 6px 0; color: #6b7280; border-top: 1px solid #e5e7eb; padding-top: 12px;'>Tổng tiền:</td><td style='padding: 6px 0; font-weight: bold; font-size: 18px; color: #16a34a; border-top: 1px solid #e5e7eb; padding-top: 12px;'>"
                        + totalPrice + " VND</td></tr>"
                        + "      </table>"
                        + "    </div>"
                        + "    <div style='background-color: #f9fafb; padding: 15px; text-align: center; border-top: 1px dashed #e5e7eb; color: #6b7280; font-size: 13px;'>"
                        + "      <p style='margin: 0;'>Vui lòng đưa mã vé này cho nhân viên tại quầy để nhận vé vào phòng chiếu.</p>"
                        + "      <p style='margin: 5px 0 0 0; font-weight: bold; color: #374151;'>Chúc bạn xem phim vui vẻ!</p>"
                        + "    </div>"
                        + "  </div>"
                        + "</div>";

                helper.setText(htmlContent, true);
                mailSender.send(message);
                logger.info("Ticket email sent successfully to {}", toEmail);
                return;
            } catch (Exception e) {
                logger.warn("Failed sending ticket email, falling back to log", e);
            }
        }

        logger.info("Mock Ticket Email for {}: MÃ ĐƠN {}, PHIM {}, RẠP {}, PHÒNG {}, GHẾ {}", toEmail, bookingId,
                movieTitle, cinemaName, roomName, seats);
    }
}