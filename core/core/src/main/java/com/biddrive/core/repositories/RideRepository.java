package com.biddrive.core.repositories;
import com.biddrive.core.models.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride,Integer>{

    List<Ride> findByStatus(String status);

    List<Ride> findTop50ByStatusOrderByCreatedAtDesc(String status);

    boolean existsByPassengerIdAndStatusIn(Integer passengerId, Collection<String> statuses);

    List<Ride> findByPassengerIdAndStatus(Integer passengerId, String status);

    List<Ride> findByPassengerIdAndStatusIn(Integer passengerId, Collection<String> statuses);
}
