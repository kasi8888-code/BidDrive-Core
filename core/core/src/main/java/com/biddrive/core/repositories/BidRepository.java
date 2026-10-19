package com.biddrive.core.repositories;

import com.biddrive.core.models.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Integer> {
    List<Bid> findByRideId(Integer rideId);
    List<Bid> findByDriverId(Integer driverId);

    @Modifying
    @Query("UPDATE Bid b SET b.status = 'OUTBID' WHERE b.rideId = :rideId AND b.status = 'ACCEPTED_LOWEST'")
    void markPreviousLeadingBidsAsOutbid(@Param("rideId") Integer rideId);

    @Query("SELECT COUNT(b) > 0 FROM Bid b, Ride r WHERE b.rideId = r.id AND b.driverId = :driverId AND b.status = 'ACCEPTED' AND r.status = 'MATCHED'")
    boolean hasActiveMatchedTrip(@Param("driverId") Integer driverId);
}