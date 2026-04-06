package com.mutualfunds.api.mutual_fund.features.briefing.api;

import com.mutualfunds.api.mutual_fund.features.briefing.application.MorningBriefingService;
import com.mutualfunds.api.mutual_fund.features.briefing.domain.PortfolioBriefing;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/v1/briefings")
@RequiredArgsConstructor
public class BriefingController {

    private final MorningBriefingService briefingService;

    @GetMapping
    public ResponseEntity<List<PortfolioBriefing>> getBriefings(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(briefingService.getUserBriefings(userId));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<PortfolioBriefing>> getUnreadBriefings(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(briefingService.getUnreadBriefings(userId));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(Map.of("count", briefingService.getUnreadCount(userId)));
    }

    @PutMapping("/{briefingId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID briefingId, Authentication authentication) {
        extractUserId(authentication);
        briefingService.markAsRead(briefingId);
        return ResponseEntity.ok().build();
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
