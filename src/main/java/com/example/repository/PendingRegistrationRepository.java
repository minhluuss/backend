package com.example.repository;

import com.example.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByEmail(String email);
    Optional<PendingRegistration> findByUsername(String username);
    void deleteByEmail(String email);
    void deleteByExpiresAtBefore(java.time.Instant time);
}
