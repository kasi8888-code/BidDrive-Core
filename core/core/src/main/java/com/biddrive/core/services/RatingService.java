package com.biddrive.core.services;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Rating;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RatingRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;

    public RatingService(RatingRepository ratingRepository,
                         RideRepository rideRepository,
                         DriverRepository driverRepository) {
        this.ratingRepository = ratingRepository;
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional
    public Rating submitRating(Integer rideId, Integer authenticatedUserId, Integer score, String comment) {

        if (score == null || score < 1 || score > 5) {
            throw new IllegalArgumentException("Rating score must be between 1 and 5.");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (!"COMPLETED".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalStateException(
                "Cannot rate Ride #" + rideId + " — it is in status '" + ride.getStatus() +
                "'. Only COMPLETED rides can be rated."
            );
        }

        if (!ride.getPassengerId().equals(authenticatedUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Access Denied: You are not the passenger for Ride #" + rideId + "."
            );
        }

        if (ratingRepository.findByRideIdAndRaterId(rideId, authenticatedUserId).isPresent()) {
            throw new IllegalStateException(
                "You have already submitted a rating for Ride #" + rideId + "."
            );
        }

        if (ride.getDriverId() == null) {
            throw new IllegalStateException("Ride #" + rideId + " has no assigned driver to rate.");
        }

        Driver driver = driverRepository.findById(ride.getDriverId())
                .orElseThrow(() -> new RuntimeException("Assigned driver not found."));

        Rating rating = new Rating();
        rating.setRideId(rideId);
        rating.setRaterId(authenticatedUserId);
        rating.setRateeId(ride.getDriverId());
        rating.setScore(score);
        rating.setComment(comment != null && !comment.isBlank() ? comment.trim() : null);
        Rating saved = ratingRepository.save(rating);

        int totalRatings = driver.getTotalRatings() == null ? 0 : driver.getTotalRatings();
        BigDecimal oldAvg = driver.getRating() != null ? driver.getRating() : BigDecimal.valueOf(5.00);
        BigDecimal newAvg = oldAvg.multiply(BigDecimal.valueOf(totalRatings))
                                  .add(BigDecimal.valueOf(score))
                                  .divide(BigDecimal.valueOf(totalRatings + 1), 2, RoundingMode.HALF_UP);

        driver.setRating(newAvg);
        driver.setTotalRatings(totalRatings + 1);
        driverRepository.save(driver);

        System.out.printf("⭐ [Rating] Driver #%d rated %d/5 for Ride #%d. New avg: %.2f (%d ratings)%n",
                ride.getDriverId(), score, rideId, newAvg, totalRatings + 1);

        return saved;
    }

    public List<Rating> getRatingsForDriver(Integer driverId) {

        driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));
        return ratingRepository.findByRateeId(driverId);
    }

    public List<Rating> getRatingsByPassenger(Integer passengerId) {
        return ratingRepository.findByRaterId(passengerId);
    }
}
