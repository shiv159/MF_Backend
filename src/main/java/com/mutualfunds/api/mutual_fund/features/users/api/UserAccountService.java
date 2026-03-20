package com.mutualfunds.api.mutual_fund.features.users.api;

import com.mutualfunds.api.mutual_fund.features.users.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountService {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID userId);
    boolean existsByEmail(String email);
    User save(User user);
    User getByEmail(String email);
    User getById(UUID userId);
}
