package com.mutualfunds.api.mutual_fund.security;

import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new UserPrincipal(
                user.getEmail(),
                user.getPasswordHash(),
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER"),
                user.getUserId());
    }
}