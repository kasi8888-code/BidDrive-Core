package com.biddrive.core.repositories;

import com.biddrive.core.models.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Integer> {

    Optional<Passenger> findByEmail(String email);

    Optional<Passenger> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);
}
