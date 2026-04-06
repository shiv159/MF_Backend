package com.mutualfunds.api.mutual_fund.features.peers.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.peers.application.PeerComparisonService;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/v1/peers")
@RequiredArgsConstructor
public class PeerComparisonController {

    private final PeerComparisonService peerComparisonService;

    @GetMapping("/compare")
    public ResponseEntity<ObjectNode> compareToPeers(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(peerComparisonService.compareToPeers(userId));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
