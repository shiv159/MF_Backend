package com.mutualfunds.api.mutual_fund.features.users.persistence;

import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}