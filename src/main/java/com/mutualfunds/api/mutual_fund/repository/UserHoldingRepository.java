package com.mutualfunds.api.mutual_fund.repository;

import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserHoldingRepository extends JpaRepository<UserHolding, UUID> {

    List<UserHolding> findByUser_UserId(UUID userId);

    Optional<UserHolding> findByUser_UserIdAndFund_FundId(UUID userId, UUID fundId);

    @Query("SELECT uh FROM UserHolding uh JOIN FETCH uh.fund WHERE uh.user.userId = :userId")
    List<UserHolding> findByUserIdWithFund(@Param("userId") UUID userId);
}