package com.biddrive.core.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartRideRequestDto {

    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 6, message = "OTP must be between 4 and 6 digits")
    private String otp;
}
