package com.biddrive.core.services;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.DriverWallet;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.DriverWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private DriverWalletRepository walletRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private FareCalculationService fareCalculationService;

    @InjectMocks
    private WalletService walletService;

    private Ride completedAuctionRide;

    @BeforeEach
    void setUp() {
        completedAuctionRide = new Ride();
        completedAuctionRide.setId(501);
        completedAuctionRide.setDriverId(10);
        completedAuctionRide.setPassengerId(20);
        completedAuctionRide.setFinalFare(new BigDecimal("200.00"));
        completedAuctionRide.setRideType("AUCTION");
        completedAuctionRide.setStatus("COMPLETED");
    }

    @Test
    @DisplayName("Credit Wallet: First ride lazily creates wallet and credits 85% for auction")
    void testCreditEarningsFirstRideLazyCreation() {
        // Driver earning: 85% of 200 = 170.00, commission = 30.00
        when(fareCalculationService.calculateDriverEarning(new BigDecimal("200.00"), "AUCTION"))
                .thenReturn(new BigDecimal("170.00"));
        when(fareCalculationService.calculateCommission(new BigDecimal("200.00"), "AUCTION"))
                .thenReturn(new BigDecimal("30.00"));

        // No wallet exists yet
        when(walletRepository.findByDriverId(10)).thenReturn(Optional.empty());
        when(walletRepository.save(any(DriverWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverWallet wallet = walletService.creditEarningsForCompletedRide(completedAuctionRide);

        assertNotNull(wallet);
        assertEquals(10, wallet.getDriverId());
        assertEquals(new BigDecimal("170.00"), wallet.getAvailableBalance());
        assertEquals(new BigDecimal("200.00"), wallet.getTotalGrossEarnings());
        assertEquals(new BigDecimal("30.00"), wallet.getTotalCommissionPaid());
        assertEquals(1, wallet.getTotalTripsCompleted());

        verify(walletRepository, times(1)).save(any(DriverWallet.class));
    }

    @Test
    @DisplayName("Credit Wallet: Accumulates balance correctly for existing wallet")
    void testCreditEarningsExistingWallet() {
        DriverWallet existingWallet = new DriverWallet();
        existingWallet.setId(1);
        existingWallet.setDriverId(10);
        existingWallet.setAvailableBalance(new BigDecimal("500.00"));
        existingWallet.setTotalGrossEarnings(new BigDecimal("600.00"));
        existingWallet.setTotalCommissionPaid(new BigDecimal("100.00"));
        existingWallet.setTotalTripsCompleted(3);

        when(fareCalculationService.calculateDriverEarning(new BigDecimal("200.00"), "AUCTION"))
                .thenReturn(new BigDecimal("170.00"));
        when(fareCalculationService.calculateCommission(new BigDecimal("200.00"), "AUCTION"))
                .thenReturn(new BigDecimal("30.00"));
        when(walletRepository.findByDriverId(10)).thenReturn(Optional.of(existingWallet));
        when(walletRepository.save(any(DriverWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverWallet updated = walletService.creditEarningsForCompletedRide(completedAuctionRide);

        assertEquals(new BigDecimal("670.00"), updated.getAvailableBalance(), "500 + 170 = 670");
        assertEquals(new BigDecimal("800.00"), updated.getTotalGrossEarnings(), "600 + 200 = 800");
        assertEquals(new BigDecimal("130.00"), updated.getTotalCommissionPaid(), "100 + 30 = 130");
        assertEquals(4, updated.getTotalTripsCompleted(), "3 + 1 = 4 trips");
    }

    @Test
    @DisplayName("Credit Wallet Validation: Rejects ride with missing driver or fare")
    void testCreditEarningsValidationErrors() {
        Ride invalidRideNoDriver = new Ride();
        invalidRideNoDriver.setId(502);
        invalidRideNoDriver.setFinalFare(new BigDecimal("100.00"));

        assertThrows(IllegalStateException.class, () ->
                walletService.creditEarningsForCompletedRide(invalidRideNoDriver)
        );

        Ride invalidRideNoFare = new Ride();
        invalidRideNoFare.setId(503);
        invalidRideNoFare.setDriverId(10);

        assertThrows(IllegalStateException.class, () ->
                walletService.creditEarningsForCompletedRide(invalidRideNoFare)
        );
    }

    @Test
    @DisplayName("Get Wallet: Returns empty zero-balance wallet for driver without rides")
    void testGetWalletForNewDriver() {
        Driver driver = new Driver();
        driver.setId(10);
        when(driverRepository.findById(10)).thenReturn(Optional.of(driver));
        when(walletRepository.findByDriverId(10)).thenReturn(Optional.empty());

        DriverWallet wallet = walletService.getWalletByDriverId(10);

        assertNotNull(wallet);
        assertEquals(10, wallet.getDriverId());
        assertEquals(BigDecimal.ZERO, wallet.getAvailableBalance());
    }
}
