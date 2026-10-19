package com.biddrive.core.controllers;

import com.biddrive.core.models.DriverWallet;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.FareCalculationService;
import com.biddrive.core.services.WalletService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final JwtUtil jwtUtil;
    private final FareCalculationService fareCalculationService;

    public WalletController(WalletService walletService,
                            JwtUtil jwtUtil,
                            FareCalculationService fareCalculationService) {
        this.walletService = walletService;
        this.jwtUtil = jwtUtil;
        this.fareCalculationService = fareCalculationService;
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('DRIVER')")
    public DriverWallet getMyWallet(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedDriverId = jwtUtil.extractUserId(token);
        return walletService.getWalletByDriverId(authenticatedDriverId);
    }

    @GetMapping("/fare-info")
    public Map<String, Object> getFareInfo() {
        return Map.of(
            "platformCommissionRate", fareCalculationService.getPlatformCommissionRate(),
            "platformCommissionPercent", String.format("%.0f%%", fareCalculationService.getPlatformCommissionRate() * 100),
            "note", "Driver earns " + String.format("%.0f%%", (1 - fareCalculationService.getPlatformCommissionRate()) * 100) + " of the final agreed fare."
        );
    }
}
