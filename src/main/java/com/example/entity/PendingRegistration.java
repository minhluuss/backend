package com.example.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pending_registration")
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String encodedPassword;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEncodedPassword() { return encodedPassword; }
    public void setEncodedPassword(String encodedPassword) { this.encodedPassword = encodedPassword; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
