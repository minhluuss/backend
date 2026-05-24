package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id") // Ép dùng đúng tên cột Id viết hoa
    private Integer id;

    @Column(name = "CinemaId") // Ép dùng đúng tên cột CinemaId
    private Integer cinemaId;

    @Column(name = "Name") // Ép dùng đúng tên cột Name
    private String name;

    // Getter & Setter (Rất quan trọng)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCinemaId() { return cinemaId; }
    public void setCinemaId(Integer cinemaId) { this.cinemaId = cinemaId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}