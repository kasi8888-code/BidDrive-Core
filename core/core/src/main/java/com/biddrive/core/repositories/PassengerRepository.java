package com.biddrive.core.repositories;

import com.biddrive.core.models.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Integer> {
    // Spring Data JPA generates "SELECT * FROM passengers WHERE email = ?" from the method name
    Optional<Passenger> findByEmail(String email);
}
