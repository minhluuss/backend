package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendOtp(String toEmail, String code) {
        // If JavaMailSender configured, send real email; otherwise log the OTP
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

        // fallback: log
        logger.info("OTP for {} = {} (use SMTP to send real email)", toEmail, code);
    }
}
