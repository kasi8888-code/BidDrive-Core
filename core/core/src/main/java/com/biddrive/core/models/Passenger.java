package com.biddrive.core.models;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Entity
@Table(name="passengers")
@Data
public class Passenger{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable=false,length=100)
    private String name;
    @Column(name="phone_number",nullable=false,unique=true,length=15)
    private String phoneNumber;
    @Column(nullable=false,unique=true,length=100)
     private String email;
     @Column(name="created_at",updatable=false,nullable=false)
     private LocalDateTime createdAt=LocalDateTime.now();
     
}
