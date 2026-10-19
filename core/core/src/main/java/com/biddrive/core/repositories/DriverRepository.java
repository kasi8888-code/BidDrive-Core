package com.biddrive.core.repositories;

import com.biddrive.core.models.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Integer> {

    Optional<Driver> findByEmail(String email);

    Optional<Driver> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Driver> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);

    List<Driver> findTop100ByStatus(String status);
}
