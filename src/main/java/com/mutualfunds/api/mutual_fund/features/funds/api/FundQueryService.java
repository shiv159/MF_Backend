package com.mutualfunds.api.mutual_fund.features.funds.api;

import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundQueryService {
    Optional<Fund> findByIsin(String isin);
    Optional<Fund> findById(UUID fundId);
    List<Fund> findByFundNameContainingIgnoreCase(String name);
    List<Fund> findAll();
    List<Fund> findAllById(Iterable<UUID> ids);
}
