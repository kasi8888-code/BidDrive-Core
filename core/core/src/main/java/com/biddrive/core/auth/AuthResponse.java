package com.biddrive.core.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private Integer userId;
    private String email;
    private String status;   // "VERIFIED", "PENDING_VERIFICATION"
    private String message;
    private String testOtp;  // Exposes OTP for 1-click dev testing in BlueStacks

    public AuthResponse(String token, String role, Integer userId, String email) {
        this.token = token;
        this.role = role;
        this.userId = userId;
        this.email = email;
        this.status = "VERIFIED";
        this.message = "Authentication successful";
    }
}
