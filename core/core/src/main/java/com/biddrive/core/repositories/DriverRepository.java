package com.biddrive.core.repositories;

import com.biddrive.core.models.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Integer> {
    // Spring Data JPA generates "SELECT * FROM drivers WHERE email = ?" from the method name
    Optional<Driver> findByEmail(String email);
}
