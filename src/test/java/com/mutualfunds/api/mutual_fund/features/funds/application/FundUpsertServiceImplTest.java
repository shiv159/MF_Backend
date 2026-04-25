package com.mutualfunds.api.mutual_fund.features.funds.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.persistence.FundRepository;
import com.mutualfunds.api.mutual_fund.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FundUpsertServiceImplTest {

    private ObjectMapper objectMapper;
    private FundRepository fundRepository;
    private FundUpsertServiceImpl fundUpsertService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fundRepository = mock(FundRepository.class);
        fundUpsertService = new FundUpsertServiceImpl(fundRepository, objectMapper);
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

    @Test
    void upsertRejectsIncompleteManualSelectionEnrichment() {
        Map<String, Object> holding = buildCompleteHolding();
        holding.remove("topHoldings");

        when(fundRepository.findByIsin("INF179K01UT0")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fundUpsertService.upsertFromEnriched(holding, true))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("topHoldings");
    }

    @Test
    void upsertRejectsQualityFlaggedMetadataForManualSelection() {
        Map<String, Object> holding = buildCompleteHolding();
        holding.put("fundMetadata", Map.of(
                "data_quality", Map.of(
                        "quality_flags", java.util.List.of("stale_metadata"),
                        "missing_fields", java.util.List.of())));

        when(fundRepository.findByIsin("INF179K01UT0")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fundUpsertService.upsertFromEnriched(holding, true))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("fundMetadata.stale_metadata");
    }

    @Test
    void upsertKeepsExistingAnalyticsJsonWhenIncomingPayloadIsEmpty() throws Exception {
        JsonNode existingSectorAllocation = objectMapper.readTree("{\"Technology\": 25.0}");
        JsonNode existingTopHoldings = objectMapper.readTree("[{\"securityName\":\"HDFC Bank Ltd\"}]");
        JsonNode existingMetadata = objectMapper.readTree(
                "{\"name\":\"HDFC Mid Cap\",\"data_quality\":{\"quality_flags\":[],\"missing_fields\":[]}}");

        Fund existingFund = Fund.builder()
                .fundId(UUID.randomUUID())
                .isin("INF179K01UT0")
                .fundName("HDFC Mid Cap")
                .sectorAllocationJson(existingSectorAllocation)
                .topHoldingsJson(existingTopHoldings)
                .fundMetadataJson(existingMetadata)
                .build();

        Map<String, Object> holding = new HashMap<>();
        holding.put("isin", "INF179K01UT0");
        holding.put("fund_name", "HDFC Mid Cap Fund - Direct Plan - Growth");
        holding.put("sectorAllocation", Map.of());
        holding.put("topHoldings", java.util.List.of());
        holding.put("fundMetadata", Map.of());

        when(fundRepository.findByIsin("INF179K01UT0")).thenReturn(Optional.of(existingFund));
        when(fundRepository.save(any(Fund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FundUpsertService.FundUpsertResult result = fundUpsertService.upsertFromEnriched(holding, false);

        assertThat(result.created()).isFalse();
        assertThat(result.fund().getSectorAllocationJson()).isEqualTo(existingSectorAllocation);
        assertThat(result.fund().getTopHoldingsJson()).isEqualTo(existingTopHoldings);
        assertThat(result.fund().getFundMetadataJson()).isEqualTo(existingMetadata);
    }

    private Map<String, Object> buildCompleteHolding() {
        Map<String, Object> holding = new HashMap<>();
        holding.put("isin", "INF179K01UT0");
        holding.put("fund_name", "HDFC Mid Cap Fund - Direct Plan - Growth");
        holding.put("currentNav", 123.45);
        holding.put("navAsOf", "2026-04-24");
        holding.put("sectorAllocation", Map.of("Technology", 18.2));
        holding.put("topHoldings", java.util.List.of(Map.of("securityName", "HDFC Bank Ltd", "weighting", 6.2)));
        holding.put("fundMetadata", Map.of(
                "name", "HDFC Mid Cap Fund - Direct Plan - Growth",
                "data_quality", Map.of(
                        "quality_flags", java.util.List.of(),
                        "missing_fields", java.util.List.of())));
        return holding;
    }
}
