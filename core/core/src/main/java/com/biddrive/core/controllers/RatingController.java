package com.biddrive.core.controllers;

import com.biddrive.core.dtos.SubmitRatingRequestDto;
import com.biddrive.core.models.Rating;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingService ratingService;
    private final JwtUtil jwtUtil;

    public RatingController(RatingService ratingService, JwtUtil jwtUtil) {
        this.ratingService = ratingService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PASSENGER')")
    public Rating submitRating(
            @Valid @RequestBody SubmitRatingRequestDto request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);

        return ratingService.submitRating(
                request.getRideId(),
                authenticatedPassengerId,
                request.getScore(),
                request.getComment()
        );
    }

    @GetMapping("/driver/{driverId}")
    public List<Rating> getDriverRatings(@PathVariable Integer driverId) {
        return ratingService.getRatingsForDriver(driverId);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PASSENGER')")
    public List<Rating> getMySubmittedRatings(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);
        return ratingService.getRatingsByPassenger(authenticatedPassengerId);
    }
}
