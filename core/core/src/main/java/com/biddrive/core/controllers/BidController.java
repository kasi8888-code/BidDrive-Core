package com.biddrive.core.controllers;

import com.biddrive.core.models.Bid;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.BidService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;
    private final JwtUtil jwtUtil;

    public BidController(BidService bidService, JwtUtil jwtUtil) {
        this.bidService = bidService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public Bid postBid(
            @RequestBody Bid bid,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        Integer authenticatedDriverId = jwtUtil.extractUserId(token);

        bid.setDriverId(authenticatedDriverId);

        return bidService.createBid(bid);
    }

    @PutMapping("/{bidId}/accept")
    @PreAuthorize("hasRole('PASSENGER')")
    public Bid acceptBid(
            @PathVariable Integer bidId,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);
        return bidService.acceptBid(bidId, authenticatedPassengerId);
    }

    @GetMapping
    public List<Bid> getAllBids() {
        return bidService.getAllBids();
    }

    @GetMapping("/{id}")
    public Bid getBidById(@PathVariable Integer id) {
        return bidService.getBidById(id).orElse(null);
    }

    @GetMapping("/ride/{rideId}")
    public List<Bid> getBidsByRideId(@PathVariable Integer rideId) {
        return bidService.getBidsByRideId(rideId);
    }
}