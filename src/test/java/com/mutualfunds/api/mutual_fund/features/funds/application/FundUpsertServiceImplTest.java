package com.mutualfunds.api.mutual_fund.features.funds.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.persistence.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FundUpsertServiceImplTest {

    private FundRepository fundRepository;
    private FundUpsertServiceImpl fundUpsertService;

    @BeforeEach
    void setUp() {
        fundRepository = mock(FundRepository.class);
        fundUpsertService = new FundUpsertServiceImpl(fundRepository, new ObjectMapper());
    }

    @Test
    void upsertCreatesFundUsingResolvedNameWhenPresent() {
        Map<String, Object> holding = new HashMap<>();
        holding.put("isin", "INF179K01UT0");
        holding.put("input_fund_name", "HDFC Mid Cap");
        holding.put("fund_name", "HDFC Mid Cap Fund - Direct Plan - Growth");

        when(fundRepository.findByIsin("INF179K01UT0")).thenReturn(Optional.empty());
        when(fundRepository.save(any(Fund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FundUpsertService.FundUpsertResult result = fundUpsertService.upsertFromEnriched(holding, false);

        assertThat(result.created()).isTrue();
        assertThat(result.fund().getFundName()).isEqualTo("HDFC Mid Cap Fund - Direct Plan - Growth");
    }

    @Test
    void upsertRefreshesExistingFundNameWhenCanonicalNameChanges() {
        Fund existingFund = Fund.builder()
                .fundId(UUID.randomUUID())
                .isin("INF179K01UT0")
                .fundName("HDFC Mid Cap")
                .build();

        Map<String, Object> holding = new HashMap<>();
        holding.put("isin", "INF179K01UT0");
        holding.put("input_fund_name", "HDFC Mid Cap");
        holding.put("fund_name", "HDFC Mid Cap Fund - Direct Plan - Growth");

        when(fundRepository.findByIsin("INF179K01UT0")).thenReturn(Optional.of(existingFund));
        when(fundRepository.save(any(Fund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FundUpsertService.FundUpsertResult result = fundUpsertService.upsertFromEnriched(holding, false);

        assertThat(result.created()).isFalse();
        assertThat(existingFund.getFundName()).isEqualTo("HDFC Mid Cap Fund - Direct Plan - Growth");
        assertThat(result.fund().getFundName()).isEqualTo("HDFC Mid Cap Fund - Direct Plan - Growth");
    }
}
