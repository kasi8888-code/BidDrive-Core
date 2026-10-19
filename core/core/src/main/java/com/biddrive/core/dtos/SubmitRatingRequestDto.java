package com.biddrive.core.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitRatingRequestDto {

    @NotNull(message = "rideId is required.")
    private Integer rideId;

    @NotNull(message = "score is required.")
    @Min(value = 1, message = "Score must be at least 1.")
    @Max(value = 5, message = "Score cannot exceed 5.")
    private Integer score;

    private String comment;
}
