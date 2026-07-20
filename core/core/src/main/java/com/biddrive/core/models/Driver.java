package com.biddrive.core.models;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name="drivers")
@Data
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;   
    @Column(nullable=false,length=100)
    private String name;
    @Column(name="phone_number",nullable=false,unique=true,length=15)
    private String phoneNumber; 
    @Column(nullable=false,unique=true,length=100)
    private String email;
    @Column(name="license_plate",nullable =false,unique=true,length=20)
    private String licensePlate;
    @Column(name="car_type",nullable=false)
    private String carType;
    @Column(nullable=false)
    private String status = "Available";
    @Column(precision=3, scale=2, nullable=false)
    private BigDecimal rating = BigDecimal.valueOf(5.00);
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    


    

    
    
}