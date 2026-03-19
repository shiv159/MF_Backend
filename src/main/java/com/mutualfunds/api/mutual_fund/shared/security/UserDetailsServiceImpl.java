package com.mutualfunds.api.mutual_fund.shared.security;

import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserAccountService userAccountService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userAccountService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new UserPrincipal(
                user.getEmail(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "", // Handle null password for OAuth users
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER"),
                user.getUserId());
    }
}
