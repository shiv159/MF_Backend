package com.mutualfunds.api.mutual_fund.security.oauth2;

import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.AuthProvider;
import com.mutualfunds.api.mutual_fund.enums.UserType;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.security.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

/**
 * Handles successful OAuth 2.0 login from Google.
 * - Extracts user info from the OAuth2 response
 * - Creates or updates user in the database
 * - Generates JWT and redirects to frontend with token
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JWTUtil jwtUtil;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        log.info("OAuth2 login success for email: {}", email);

        // Find existing user or create new one
        User user = processOAuthUser(email, name);

        // Generate JWT token
        String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getUserId());

        // Build redirect URL with token
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                .queryParam("token", jwtToken)
                .build().toUriString();

        log.info("Redirecting to frontend: {}", targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private User processOAuthUser(String email, String name) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Update name if changed
            if (name != null && !name.equals(user.getFullName())) {
                user.setFullName(name);
                user = userRepository.save(user);
                log.info("Updated existing OAuth user: {}", email);
            }
            return user;
        }

        // Create new user for OAuth login
        User newUser = User.builder()
                .email(email)
                .fullName(name)
                .authProvider(AuthProvider.GOOGLE)
                .userType(UserType.new_investor)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Created new OAuth user: {}", email);
        return savedUser;
    }
}
