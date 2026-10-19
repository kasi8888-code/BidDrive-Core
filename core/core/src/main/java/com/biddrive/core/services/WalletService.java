package com.biddrive.core.services;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.DriverWallet;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.DriverWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class WalletService {

    private final DriverWalletRepository walletRepository;
    private final DriverRepository driverRepository;
    private final FareCalculationService fareCalculationService;

    public WalletService(DriverWalletRepository walletRepository,
                         DriverRepository driverRepository,
                         FareCalculationService fareCalculationService) {
        this.walletRepository = walletRepository;
        this.driverRepository = driverRepository;
        this.fareCalculationService = fareCalculationService;
    }

    @Transactional
    public DriverWallet creditEarningsForCompletedRide(Ride ride) {
        if (ride.getDriverId() == null) {
            throw new IllegalStateException("Ride #" + ride.getId() + " has no assigned driver — cannot credit wallet.");
        }
        if (ride.getFinalFare() == null || ride.getFinalFare().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Ride #" + ride.getId() + " has no valid finalFare — cannot credit wallet.");
        }

        BigDecimal grossFare = ride.getFinalFare();

        String rideType = ride.getRideType() != null ? ride.getRideType() : "INSTANT";
        BigDecimal driverEarning = fareCalculationService.calculateDriverEarning(grossFare, rideType);
        BigDecimal commission = fareCalculationService.calculateCommission(grossFare, rideType);

        DriverWallet wallet = walletRepository.findByDriverId(ride.getDriverId())
                .orElseGet(() -> {
                    DriverWallet newWallet = new DriverWallet();
                    newWallet.setDriverId(ride.getDriverId());
                    return newWallet;
                });

        wallet.setAvailableBalance(wallet.getAvailableBalance().add(driverEarning));
        wallet.setTotalGrossEarnings(wallet.getTotalGrossEarnings().add(grossFare));
        wallet.setTotalCommissionPaid(wallet.getTotalCommissionPaid().add(commission));
        wallet.setTotalTripsCompleted(wallet.getTotalTripsCompleted() + 1);
        wallet.setUpdatedAt(LocalDateTime.now());

        DriverWallet saved = walletRepository.save(wallet);

        System.out.printf("💰 [Wallet] Driver #%d credited ₹%.2f (gross: ₹%.2f, commission: ₹%.2f) for Ride #%d%n",
                ride.getDriverId(), driverEarning, grossFare, commission, ride.getId());

        return saved;
    }

    public DriverWallet getWalletByDriverId(Integer driverId) {

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));

        return walletRepository.findByDriverId(driverId)
                .orElseGet(() -> {

                    DriverWallet empty = new DriverWallet();
                    empty.setDriverId(driverId);
                    return empty;
                });
    }
}
