package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Seats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"RoomId", "SeatRow", "SeatNumber"})
})
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "RoomId")
    private Integer roomId;

    @Column(name = "SeatRow")
    private String seatRow;

    @Column(name = "SeatNumber")
    private Integer seatNumber;

    @Column(name = "Type")
    private String type;

    // Getter & Setter
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public String getSeatRow() { return seatRow; }
    public void setSeatRow(String seatRow) { this.seatRow = seatRow; }

    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}