package com.biddrive.core.repositories;

import com.biddrive.core.models.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Integer> {

    List<Rating> findByRateeId(Integer driverId);

    List<Rating> findByRaterId(Integer passengerId);

    Optional<Rating> findByRideIdAndRaterId(Integer rideId, Integer raterId);
}
