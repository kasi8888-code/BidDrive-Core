package com.biddrive.core.controllers;

import com.biddrive.core.models.Bid;
import com.biddrive.core.services.BidService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping
    public Bid postBid(@RequestBody Bid bid) {
        return bidService.createBid(bid);
    }

    @PutMapping("/{bidId}/accept")
    public Bid acceptBid(@PathVariable Integer bidId) {
        return bidService.acceptBid(bidId);
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
