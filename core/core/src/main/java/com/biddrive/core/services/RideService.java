package com.biddrive.core.services;

import com.biddrive.core.dtos.NearbyDriverDto;
import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RideService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "INITIATED", "MATCHED", "DRIVER_ARRIVING", "ARRIVING", "ARRIVED", "IN_PROGRESS");

    private final RideRepository rideRepository;
    private final PassengerRepository passengerRepository;
    private final BidRepository bidRepository;
    private final AuctionRedisService auctionRedisService;
    private final AuctionEventPublisher auctionEventPublisher;
    private final GeoDistanceService geoDistanceService;
    private final DriverService driverService;
    private final WalletService walletService;
    private final FareCalculationService fareCalculationService;
    private final DriverRepository driverRepository;

    public RideService(RideRepository rideRepository,
                       PassengerRepository passengerRepository,
                       BidRepository bidRepository,
                       AuctionRedisService auctionRedisService,
                       AuctionEventPublisher auctionEventPublisher,
                       GeoDistanceService geoDistanceService,
                       DriverService driverService,
                       WalletService walletService,
                       FareCalculationService fareCalculationService,
                       DriverRepository driverRepository) {
        this.rideRepository = rideRepository;
        this.passengerRepository = passengerRepository;
        this.bidRepository = bidRepository;
        this.auctionRedisService = auctionRedisService;
        this.auctionEventPublisher = auctionEventPublisher;
        this.geoDistanceService = geoDistanceService;
        this.driverService = driverService;
        this.walletService = walletService;
        this.fareCalculationService = fareCalculationService;
        this.driverRepository = driverRepository;
    }

    @Transactional
    public Ride retryAuction(Integer rideId, Integer passengerId, BigDecimal newBaseFare) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        if (!ride.getPassengerId().equals(passengerId)) throw new RuntimeException("Unauthorized");
        if (!"EXPIRED".equals(ride.getStatus())) throw new RuntimeException("Ride is not expired");

        ride.setStatus("INITIATED");
        if (newBaseFare != null) {
            ride.setBaseFare(newBaseFare);
        }
        Ride savedRide = rideRepository.save(ride);

        auctionRedisService.initAuction(savedRide.getId(), savedRide.getBaseFare());

        List<NearbyDriverDto> nearbyDrivers = null;
        if (savedRide.getPickupLatitude() != null && savedRide.getPickupLongitude() != null) {
            nearbyDrivers = driverService.findNearbyDrivers(
                    savedRide.getPickupLatitude(), savedRide.getPickupLongitude(), 5.0);
        }
        auctionEventPublisher.publishRideCreated(savedRide, nearbyDrivers);

        return savedRide;
    }

    @Transactional
    public Ride convertToInstant(Integer rideId, Integer passengerId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        if (!ride.getPassengerId().equals(passengerId)) throw new RuntimeException("Unauthorized");
        if (!"INITIATED".equalsIgnoreCase(ride.getStatus()) && !"EXPIRED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Ride cannot be converted to instant in status: " + ride.getStatus());
        }

        auctionRedisService.closeAuction(ride.getId());

        return createInstantRide(ride);
    }

    public List<Ride> getRides(String status, String driverCarType) {
        List<Ride> rides;
        if (status != null && !status.isBlank()) {

            rides = rideRepository.findTop50ByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            rides = rideRepository.findAll();
        }

        if (driverCarType != null && !driverCarType.isBlank()) {
            rides = rides.stream()
                    .filter(r -> isVehicleCompatible(r.getVehicleType(), driverCarType))
                    .toList();
        }

        if ("INITIATED".equalsIgnoreCase(status)) {
            rides = rides.stream()
                    .filter(r -> !"AUCTION".equalsIgnoreCase(r.getRideType()) || auctionRedisService.isAuctionActive(r.getId()))
                    .toList();
        }

        return rides;
    }

    public Long getRemainingSeconds(Integer rideId) {
        return auctionRedisService.getRemainingSeconds(rideId);
    }

    public List<Ride> getRides(String status) {
        return getRides(status, null);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Optional<Ride> getRideById(Integer id) {
        return rideRepository.findById(id);
    }

    public java.util.Map<Object, Object> getLiveAuction(Integer rideId) {
        return auctionRedisService.getAuction(rideId);
    }

    public Ride createRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }

        List<Ride> staleInitiatedRides = rideRepository.findByPassengerIdAndStatus(ride.getPassengerId(), "INITIATED");
        for (Ride r : staleInitiatedRides) {
            try {
                auctionRedisService.closeAuction(r.getId());
            } catch (Exception ignored) {}
            r.setStatus("CANCELLED");
            r.setCancellationReason("Superseded by new ride booking");
            r.setCancelledBy("PASSENGER");
            r.setCancelledAt(java.time.LocalDateTime.now());
            rideRepository.save(r);
        }

        boolean hasActiveRide = rideRepository.existsByPassengerIdAndStatusIn(
                ride.getPassengerId(), ACTIVE_STATUSES);
        if (hasActiveRide) {
            throw new IllegalStateException("You already have an active ride in progress. Complete or cancel it before booking a new one.");
        }

        if (ride.getPickupLatitude() != null && ride.getPickupLongitude() != null
                && ride.getDestinationLatitude() != null && ride.getDestinationLongitude() != null) {
            double distanceKm = geoDistanceService.calculateDistanceKm(
                    ride.getPickupLatitude(), ride.getPickupLongitude(),
                    ride.getDestinationLatitude(), ride.getDestinationLongitude());
            ride.setEstimatedDistanceKm(distanceKm);

            if (ride.getBaseFare() == null || ride.getBaseFare().compareTo(BigDecimal.ZERO) <= 0) {
                ride.setBaseFare(geoDistanceService.calculateSuggestedBaseFare(distanceKm));
            }
        }

        if (ride.getBaseFare() == null) {
            ride.setBaseFare(BigDecimal.valueOf(50.00));
        }

        Ride savedRide = rideRepository.save(ride);

        auctionRedisService.initAuction(savedRide.getId(), savedRide.getBaseFare());

        List<NearbyDriverDto> nearbyDrivers = null;
        if (savedRide.getPickupLatitude() != null && savedRide.getPickupLongitude() != null) {
            nearbyDrivers = driverService.findNearbyDrivers(
                    savedRide.getPickupLatitude(), savedRide.getPickupLongitude(), 5.0);
        }

        auctionEventPublisher.publishRideCreated(savedRide, nearbyDrivers);

        return savedRide;
    }

    @Transactional
    public Ride createInstantRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }

        List<Ride> staleInitiatedRides = rideRepository.findByPassengerIdAndStatus(ride.getPassengerId(), "INITIATED");
        for (Ride r : staleInitiatedRides) {
            try {
                auctionRedisService.closeAuction(r.getId());
            } catch (Exception ignored) {}
            r.setStatus("CANCELLED");
            r.setCancellationReason("Superseded by new instant ride");
            r.setCancelledBy("PASSENGER");
            r.setCancelledAt(java.time.LocalDateTime.now());
            rideRepository.save(r);
        }

        ride.setRideType("INSTANT");

        if (ride.getPickupLatitude() != null && ride.getPickupLongitude() != null
                && ride.getDestinationLatitude() != null && ride.getDestinationLongitude() != null) {
            double distanceKm = geoDistanceService.calculateDistanceKm(
                    ride.getPickupLatitude(), ride.getPickupLongitude(),
                    ride.getDestinationLatitude(), ride.getDestinationLongitude());
            ride.setEstimatedDistanceKm(distanceKm);

            BigDecimal platformFare = fareCalculationService.calculateFare(distanceKm, 0);
            ride.setBaseFare(platformFare);
            ride.setFinalFare(platformFare);
        } else {

            BigDecimal fallback = BigDecimal.valueOf(50.00);
            ride.setBaseFare(fallback);
            ride.setFinalFare(fallback);
        }

        if (ride.getPickupLatitude() == null || ride.getPickupLongitude() == null) {
            throw new RuntimeException("Pickup coordinates required for instant booking.");
        }

        List<NearbyDriverDto> nearbyDrivers = driverService.findNearbyDrivers(
                ride.getPickupLatitude(), ride.getPickupLongitude(), 5.0);

        Optional<NearbyDriverDto> nearestDriverOpt = nearbyDrivers.stream()
                .filter(d -> {
                    if (!isVehicleCompatible(ride.getVehicleType(), d.getCarType())) {
                        return false;
                    }
                    Optional<Driver> dr = driverRepository.findById(d.getDriverId());
                    if (dr.isEmpty()) return false;
                    String pref = dr.get().getPreferenceMode();
                    return pref == null || "BOTH".equalsIgnoreCase(pref) || "INSTANT_ONLY".equalsIgnoreCase(pref);
                })
                .sorted((d1, d2) -> {

                    BigDecimal r1 = d1.getRating() != null ? d1.getRating() : BigDecimal.valueOf(5.00);
                    BigDecimal r2 = d2.getRating() != null ? d2.getRating() : BigDecimal.valueOf(5.00);
                    int ratingCompare = r2.compareTo(r1);
                    if (ratingCompare != 0) return ratingCompare;

                    double dist1 = d1.getDistanceKm() != null ? d1.getDistanceKm() : 0.0;
                    double dist2 = d2.getDistanceKm() != null ? d2.getDistanceKm() : 0.0;
                    return Double.compare(dist1, dist2);
                })
                .findFirst();

        Driver assignedDriver = null;
        if (nearestDriverOpt.isPresent()) {
            assignedDriver = driverRepository.findById(nearestDriverOpt.get().getDriverId()).orElse(null);
        }

        if (assignedDriver == null) {
            throw new IllegalStateException("No nearby drivers found within 5.0 km for " + (ride.getVehicleType() != null ? ride.getVehicleType() : "this vehicle") + ". All nearby drivers are currently offline or busy. Try again or switch to Reverse Auction.");
        }

        Integer assignedDriverId = assignedDriver.getId();

        String otp = String.format("%04d", new java.security.SecureRandom().nextInt(10000));

        ride.setStatus("MATCHED");
        ride.setDriverId(assignedDriverId);
        ride.setOtp(otp);
        Ride savedRide = rideRepository.save(ride);

        assignedDriver.setStatus("BUSY");
        driverRepository.save(assignedDriver);

        auctionEventPublisher.publishAuctionMatched(savedRide, assignedDriverId, savedRide.getFinalFare());

        return savedRide;
    }

    public Ride updateRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }
        return rideRepository.save(ride);
    }

    public void deleteRide(Integer id, Integer authenticatedPassengerId) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));

        if (!ride.getPassengerId().equals(authenticatedPassengerId)) {
            throw new RuntimeException("Access Denied: You do not own this ride.");
        }

        rideRepository.deleteById(id);
    }

    public Ride markDriverArriving(Integer rideId, Integer driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not the assigned driver for Ride #" + rideId);
        }

        if (!"MATCHED".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalStateException("Cannot mark arriving. Ride #" + rideId + " is in status " + ride.getStatus() + " (Expected: MATCHED).");
        }

        ride.setStatus("DRIVER_ARRIVING");
        Ride savedRide = rideRepository.save(ride);

        driverService.getDriverById(driverId).ifPresent(driver ->
            auctionEventPublisher.publishDriverArriving(savedRide, driver)
        );

        return savedRide;
    }

    public Ride markDriverArrived(Integer rideId, Integer driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not the assigned driver for Ride #" + rideId);
        }

        if (!"DRIVER_ARRIVING".equalsIgnoreCase(ride.getStatus()) && !"MATCHED".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalStateException("Cannot mark arrived. Ride #" + rideId + " is in status " + ride.getStatus() + " (Expected: DRIVER_ARRIVING or MATCHED).");
        }

        ride.setStatus("ARRIVED");
        Ride savedRide = rideRepository.save(ride);

        driverService.getDriverById(driverId).ifPresent(driver ->
            auctionEventPublisher.publishDriverArrived(savedRide, driver)
        );

        return savedRide;
    }

    public Ride startTrip(Integer rideId, Integer driverId, String enteredOtp) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not the assigned driver for Ride #" + rideId);
        }

        if (!"ARRIVED".equalsIgnoreCase(ride.getStatus()) && !"DRIVER_ARRIVING".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalStateException("Cannot start trip. Ride #" + rideId + " status is " + ride.getStatus() + " (Driver must arrive first).");
        }

        if (enteredOtp == null || ride.getOtp() == null || !ride.getOtp().trim().equals(enteredOtp.trim())) {
            throw new IllegalArgumentException("Invalid OTP. Trip cannot start without valid passenger verification.");
        }

        ride.setStatus("IN_PROGRESS");
        ride.setStartedAt(java.time.LocalDateTime.now());
        Ride savedRide = rideRepository.save(ride);

        auctionEventPublisher.publishTripStarted(savedRide);

        return savedRide;
    }

    public Ride completeRide(Integer rideId, Integer driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (driverId != null && (ride.getDriverId() == null || !ride.getDriverId().equals(driverId))) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not the assigned driver for Ride #" + rideId);
        }

        if (!"IN_PROGRESS".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalStateException("Cannot complete ride #" + rideId + ". Status must be IN_PROGRESS (current: " + ride.getStatus() + ").");
        }

        ride.setStatus("COMPLETED");
        ride.setCompletedAt(java.time.LocalDateTime.now());
        Ride savedRide = rideRepository.save(ride);

        Integer assignedDriverId = ride.getDriverId();
        if (assignedDriverId != null) {
            driverService.updateDriverStatus(assignedDriverId, "Available");

        }

        walletService.creditEarningsForCompletedRide(savedRide);

        auctionEventPublisher.publishRideCompleted(savedRide);

        return savedRide;
    }

    public Ride cancelRide(Integer rideId, Integer requesterUserId, String requesterRole, String reason) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        String currentStatus = ride.getStatus();
        if ("IN_PROGRESS".equalsIgnoreCase(currentStatus) || "COMPLETED".equalsIgnoreCase(currentStatus)
                || "CANCELLED".equalsIgnoreCase(currentStatus) || "EXPIRED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Ride #" + rideId + " cannot be cancelled in status: " + currentStatus);
        }

        boolean isDriver = requesterRole != null && requesterRole.contains("DRIVER");
        boolean isPassenger = requesterRole != null && requesterRole.contains("PASSENGER");

        if (isPassenger && !ride.getPassengerId().equals(requesterUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You do not own this ride.");
        }
        if (isDriver && (ride.getDriverId() == null || !ride.getDriverId().equals(requesterUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not assigned to this ride.");
        }

        if ("INITIATED".equalsIgnoreCase(currentStatus)) {
            auctionRedisService.closeAuction(ride.getId());
        }

        ride.setStatus("CANCELLED");
        ride.setCancelledAt(java.time.LocalDateTime.now());
        ride.setCancellationReason(reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by " + (isDriver ? "driver" : "passenger"));
        ride.setCancelledBy(isDriver ? "DRIVER" : "PASSENGER");
        Ride savedRide = rideRepository.save(ride);

        if (ride.getDriverId() != null) {
            driverService.updateDriverStatus(ride.getDriverId(), "Available");

        }

        auctionEventPublisher.publishTripCancelled(savedRide, ride.getCancellationReason(), ride.getCancelledBy());

        return savedRide;
    }

    public static boolean isVehicleCompatible(String requestedVehicleType, String driverCarType) {
        if (requestedVehicleType == null || requestedVehicleType.isBlank()) {
            return false;
        }
        if (driverCarType == null || driverCarType.isBlank()) {
            return false;
        }

        String req = requestedVehicleType.trim().toLowerCase();
        String car = driverCarType.trim().toLowerCase();

        if (req.equals(car)) {
            return true;
        }

        if (req.equals("bike") || req.equals("motorcycle")) {
            return car.equals("bike") || car.equals("motorcycle");
        }
        if (req.equals("scooty") || req.equals("scooter")) {
            return car.equals("scooty") || car.equals("scooter");
        }

        if (req.contains("auto")) {
            return car.contains("auto");
        }

        if (req.equals("hatchback")) {
            return car.equals("hatchback");
        }
        if (req.equals("sedan")) {
            return car.equals("sedan");
        }
        if (req.equals("suv")) {
            return car.equals("suv");
        }

        if (req.contains("economy") || req.contains("mini")) {
            return car.contains("hatchback") || car.contains("economy") || car.contains("mini") || car.contains("sedan");
        }
        if (req.contains("priority")) {
            return car.contains("sedan") || car.contains("suv") || car.contains("priority");
        }

        return false;
    }

    @Transactional
    public List<Bid> simulateBids(Integer rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Auction is not active");
        }

        List<Driver> drivers = new ArrayList<>(driverRepository.findAll());
        if (drivers.isEmpty()) {
            Driver demo1 = new Driver();
            demo1.setName("Rajesh Kumar");
            demo1.setEmail("rajesh.demo" + System.currentTimeMillis() + "@biddrive.com");
            demo1.setPhoneNumber("+91" + (9000000000L + (long)(Math.random() * 999999999L)));
            demo1.setPassword("$2a$10$abcdefghijkdemoPasswordHash123");
            demo1.setCarType("Sedan");
            demo1.setLicensePlate("KA-01-AB-" + (1000 + (int)(Math.random() * 9000)));
            demo1.setStatus("Available");
            demo1.setPreferenceMode("BOTH");
            demo1.setRating(BigDecimal.valueOf(4.95));
            demo1 = driverRepository.save(demo1);
            drivers.add(demo1);

            Driver demo2 = new Driver();
            demo2.setName("Suresh Reddy");
            demo2.setEmail("suresh.demo" + System.currentTimeMillis() + "@biddrive.com");
            demo2.setPhoneNumber("+91" + (9000000000L + (long)(Math.random() * 999999999L)));
            demo2.setPassword("$2a$10$abcdefghijkdemoPasswordHash123");
            demo2.setCarType("Mini");
            demo2.setLicensePlate("KA-02-CD-" + (1000 + (int)(Math.random() * 9000)));
            demo2.setStatus("Available");
            demo2.setPreferenceMode("BOTH");
            demo2.setRating(BigDecimal.valueOf(4.88));
            demo2 = driverRepository.save(demo2);
            drivers.add(demo2);
        }

        BigDecimal currentLowest = BigDecimal.valueOf(1000000);
        java.util.Map<Object, Object> auction = auctionRedisService.getAuction(rideId);
        if (auction != null && auction.get("lowestBid") != null) {
            currentLowest = new BigDecimal(auction.get("lowestBid").toString());
        } else if (ride.getBaseFare() != null) {
            currentLowest = ride.getBaseFare();
        }

        int existingBidsCount = bidRepository.findByRideId(rideId).size();
        Driver bidder = drivers.get(existingBidsCount % drivers.size());

        List<Bid> createdBids = new ArrayList<>();
        BigDecimal bidAmount = currentLowest.subtract(BigDecimal.valueOf(5));
        if (bidAmount.compareTo(BigDecimal.TEN) > 0) {
            Bid b = new Bid();
            b.setRideId(rideId);
            b.setDriverId(bidder.getId());
            b.setBidAmount(bidAmount);
            b.setStatus("ACCEPTED_LOWEST");
            auctionRedisService.submitLiveBid(rideId, bidder.getId(), bidAmount);
            Bid saved = bidRepository.save(b);
            auctionEventPublisher.publishNewBid(rideId, saved.getId(), bidder.getId(), bidAmount);
            createdBids.add(saved);
        }

        return createdBids;
    }

    @Transactional
    public Ride simulateDriverStep(Integer rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        Integer driverId = ride.getDriverId();
        if (driverId == null) {

            List<Bid> bids = bidRepository.findByRideId(rideId);
            Bid winner = bids.stream()
                    .filter(b -> "ACCEPTED".equalsIgnoreCase(b.getStatus()) || "ACCEPTED_LOWEST".equalsIgnoreCase(b.getStatus()))
                    .findFirst()
                    .orElse(bids.isEmpty() ? null : bids.get(0));
            if (winner != null && winner.getDriverId() != null) {
                driverId = winner.getDriverId();
                ride.setDriverId(driverId);
                if (ride.getFinalFare() == null) ride.setFinalFare(winner.getBidAmount());
                if (ride.getOtp() == null) ride.setOtp(String.format("%04d", new java.security.SecureRandom().nextInt(10000)));
                ride = rideRepository.save(ride);
            } else {
                List<Driver> allDrivers = driverRepository.findAll();
                if (!allDrivers.isEmpty()) {
                    driverId = allDrivers.get(0).getId();
                    ride.setDriverId(driverId);
                    if (ride.getOtp() == null) ride.setOtp(String.format("%04d", new java.security.SecureRandom().nextInt(10000)));
                    ride = rideRepository.save(ride);
                }
            }
        }
        if (driverId == null) {
            throw new IllegalStateException("Ride does not have an assigned driver yet. Please wait for a bid or switch to instant match.");
        }

        String currentStatus = ride.getStatus();
        if ("MATCHED".equalsIgnoreCase(currentStatus)) {
            return markDriverArriving(rideId, driverId);
        } else if ("DRIVER_ARRIVING".equalsIgnoreCase(currentStatus) || "ARRIVING".equalsIgnoreCase(currentStatus)) {
            return markDriverArrived(rideId, driverId);
        } else if ("ARRIVED".equalsIgnoreCase(currentStatus)) {
            String otp = ride.getOtp();
            if (otp == null) {
                otp = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
                ride.setOtp(otp);
                ride = rideRepository.save(ride);
            }
            return startTrip(rideId, driverId, otp);
        } else if ("IN_PROGRESS".equalsIgnoreCase(currentStatus)) {
            return completeRide(rideId, driverId);
        } else {
            throw new IllegalStateException("Cannot advance ride from status: " + currentStatus);
        }
    }
}
