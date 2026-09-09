package com.biddrive.core.controllers;

import com.biddrive.core.models.Bid;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.BidService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BidController - secured bid placement and retrieval.
 *
 * The key security enforcement here is in postBid():
 *   A driver is authenticated via their JWT (they proved who they are at the filter level).
 *   We extract the authenticated driver's userId from the JWT claims.
 *   We then FORCIBLY set bid.driverId = authenticatedDriverId, regardless of what
 *   the client sent in the request body.
 *
 * WHY overwrite the driverId from the request body?
 *   Without this, a driver (userId=5) could send { driverId: 7, ... } and place a bid
 *   pretending to be driver #7. By ignoring the client's driverId and using the one
 *   from the verified JWT, we close this impersonation vulnerability completely.
 *   This is the "server-side enforcement" of "drivers can only bid as themselves."
 *
 * WHY @PreAuthorize("hasRole('DRIVER')") IN ADDITION to SecurityConfig rules?
 *   SecurityConfig rules are URL-pattern-based - they cover the common case.
 *   @PreAuthorize is method-level and evaluated AFTER the SecurityContext is populated.
 *   Defence-in-depth: if someone misconfigures the SecurityConfig later, @PreAuthorize
 *   is still there as a second gate. Never rely on just one layer of authorisation.
 *
 * WHY @AuthenticationPrincipal UserDetails and not SecurityContextHolder.getContext()?
 *   @AuthenticationPrincipal is Spring's recommended injection mechanism for the current
 *   principal - it's cleaner and testable (you can inject a mock UserDetails in unit tests).
 *   SecurityContextHolder.getContext().getAuthentication() works but is a static call
 *   that makes unit testing harder.
 */
@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;
    private final JwtUtil jwtUtil;

    public BidController(BidService bidService, JwtUtil jwtUtil) {
        this.bidService = bidService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * POST /api/bids
     * Requires: Authorization: Bearer <DRIVER_TOKEN>
     *
     * The driverId in the request body is IGNORED - the authenticated driver's ID
     * from the JWT is used instead. This prevents impersonation.
     *
     * WHY pass the raw Authorization header and extract userId from JwtUtil?
     *   @AuthenticationPrincipal gives us UserDetails (email + authorities) but NOT
     *   our custom userId field. The userId is a custom claim in the JWT, so we
     *   extract the raw token from the header and call jwtUtil.extractUserId().
     *   Alternative: create a custom UserDetails subclass that carries userId - valid,
     *   but more complex. The header extraction is transparent and simple for now.
     */
    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public Bid postBid(
            @RequestBody Bid bid,
            @RequestHeader("Authorization") String authHeader) {

        // Extract the userId of the CURRENTLY AUTHENTICATED driver from the JWT
        String token = authHeader.substring(7); // strip "Bearer "
        Integer authenticatedDriverId = jwtUtil.extractUserId(token);

        // SECURITY: Overwrite whatever driverId the client sent with the real one from JWT
        bid.setDriverId(authenticatedDriverId);

        return bidService.createBid(bid);
    }

    /**
     * PUT /api/bids/{bidId}/accept
     * Requires: Authorization: Bearer <PASSENGER_TOKEN>
     * Restricted to PASSENGER role in SecurityConfig.
     * IDOR protection: verified server-side that caller owns the ride.
     */
    @PutMapping("/{bidId}/accept")
    @PreAuthorize("hasRole('PASSENGER')")
    public Bid acceptBid(
            @PathVariable Integer bidId,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);
        return bidService.acceptBid(bidId, authenticatedPassengerId);
    }

    /**
     * GET /api/bids
     * Requires: any authenticated user (driver or passenger can see all bids).
     */
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