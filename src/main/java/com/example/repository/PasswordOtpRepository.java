package com.example.repository;

import com.example.entity.PasswordOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface PasswordOtpRepository extends JpaRepository<PasswordOtp, Long> {
    Optional<PasswordOtp> findByEmail(String email);
    void deleteByExpiresAtBefore(Instant time);
}