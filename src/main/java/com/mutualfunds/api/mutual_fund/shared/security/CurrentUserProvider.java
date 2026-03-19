package com.mutualfunds.api.mutual_fund.shared.security;

import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserAccountService userAccountService;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        String email = authentication.getName();
        return userAccountService.getByEmail(email);
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return authentication.getName();
    }
}
