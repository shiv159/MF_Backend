package com.mutualfunds.api.mutual_fund.repository;

import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioUploadRepository extends JpaRepository<PortfolioUpload, UUID> {

    List<PortfolioUpload> findByUser_UserId(UUID userId);

    Optional<PortfolioUpload> findByUploadIdAndUser_UserId(UUID uploadId, UUID userId);
}