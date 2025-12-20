package com.mutualfunds.api.mutual_fund.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

@Getter
public class UserPrincipal extends User {
    private final UUID userId;

    public UserPrincipal(String email, String password, Collection<? extends GrantedAuthority> authorities,
            UUID userId) {
        super(email, password, authorities);
        this.userId = userId;
    }
}
