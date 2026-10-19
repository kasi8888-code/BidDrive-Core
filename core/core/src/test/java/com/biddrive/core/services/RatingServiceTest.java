package com.biddrive.core.services;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Rating;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RatingRepository;
import com.biddrive.core.repositories.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private RatingService ratingService;

    private Ride completedRide;
    private Driver driver;

    @BeforeEach
    void setUp() {
        completedRide = new Ride();
        completedRide.setId(100);
        completedRide.setPassengerId(1);
        completedRide.setDriverId(2);
        completedRide.setStatus("COMPLETED");

        driver = new Driver();
        driver.setId(2);
        driver.setName("Kavitha");
        driver.setRating(new BigDecimal("5.00"));
        driver.setTotalRatings(1);
    }

    @Test
    @DisplayName("Submit Rating: Successfully rates driver and computes new incremental average")
    void testSubmitRatingSuccess() {
        when(rideRepository.findById(100)).thenReturn(Optional.of(completedRide));
        when(ratingRepository.findByRideIdAndRaterId(100, 1)).thenReturn(Optional.empty());
        when(driverRepository.findById(2)).thenReturn(Optional.of(driver));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Passenger gives a 4-star rating:
        // oldAvg = 5.00, totalRatings = 1
        // newAvg = ((5.00 * 1) + 4) / (1 + 1) = 9.00 / 2 = 4.50
        Rating rating = ratingService.submitRating(100, 1, 4, "Smooth and safe ride!");

        assertNotNull(rating);
        assertEquals(4, rating.getScore());
        assertEquals("Smooth and safe ride!", rating.getComment());
        assertEquals(1, rating.getRaterId());
        assertEquals(2, rating.getRateeId());

        // Verify driver updated
        assertEquals(new BigDecimal("4.50"), driver.getRating());
        assertEquals(2, driver.getTotalRatings());
        verify(driverRepository, times(1)).save(driver);
    }

    @Test
    @DisplayName("IDOR Protection: Rejects rating if rater is not the ride passenger")
    void testSubmitRatingIdorProtection() {
        when(rideRepository.findById(100)).thenReturn(Optional.of(completedRide));

        // Authenticated user is #99, but passenger is #1
        assertThrows(AccessDeniedException.class, () ->
                ratingService.submitRating(100, 99, 5, "Hacked rating attempt")
        );
    }

    @Test
    @DisplayName("Status Rule: Rejects rating if ride is still IN_PROGRESS or CANCELLED")
    void testSubmitRatingNonCompletedRide() {
        completedRide.setStatus("IN_PROGRESS");
        when(rideRepository.findById(100)).thenReturn(Optional.of(completedRide));

        assertThrows(IllegalStateException.class, () ->
                ratingService.submitRating(100, 1, 5, "Too early")
        );
    }

    @Test
    @DisplayName("Duplicate Prevention: Rejects duplicate rating for the same ride")
    void testSubmitDuplicateRatingRejected() {
        when(rideRepository.findById(100)).thenReturn(Optional.of(completedRide));
        when(ratingRepository.findByRideIdAndRaterId(100, 1)).thenReturn(Optional.of(new Rating()));

        assertThrows(IllegalStateException.class, () ->
                ratingService.submitRating(100, 1, 5, "Duplicate")
        );
    }

    @Test
    @DisplayName("Score Validation: Rejects invalid star ratings (e.g. 0 or 6)")
    void testSubmitInvalidScoreRange() {
        assertThrows(IllegalArgumentException.class, () ->
                ratingService.submitRating(100, 1, 0, "Invalid")
        );
        assertThrows(IllegalArgumentException.class, () ->
                ratingService.submitRating(100, 1, 6, "Invalid")
        );
    }
}
