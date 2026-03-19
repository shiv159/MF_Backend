package com.mutualfunds.api.mutual_fund.features.funds.application;

import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.persistence.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FundQueryServiceImpl implements FundQueryService {

    private final FundRepository fundRepository;

    @Override
    public Optional<Fund> findByIsin(String isin) {
        return fundRepository.findByIsin(isin);
    }

    @Override
    public Optional<Fund> findById(UUID fundId) {
        return fundRepository.findById(fundId);
    }

    @Override
    public List<Fund> findByFundNameContainingIgnoreCase(String name) {
        return fundRepository.findByFundNameContainingIgnoreCase(name);
    }

    @Override
    public List<Fund> findAll() {
        return fundRepository.findAll();
    }

    @Override
    public List<Fund> findAllById(Iterable<UUID> ids) {
        return fundRepository.findAllById(ids);
    }
}
