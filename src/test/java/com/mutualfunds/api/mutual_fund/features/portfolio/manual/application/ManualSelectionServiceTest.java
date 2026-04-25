package com.mutualfunds.api.mutual_fund.features.portfolio.manual.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.application.PortfolioAnalyzerService;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.application.WealthProjectionService;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.dto.WealthProjectionDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.persistence.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionItemRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionResponse;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.api.IOnboardingService;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.application.ETLEnrichmentService;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.dto.EnrichmentResult;
import com.mutualfunds.api.mutual_fund.features.risk.dto.PortfolioHealthDTO;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualSelectionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IOnboardingService onboardingService;
    private FundQueryService fundQueryService;
    private FundUpsertService fundUpsertService;
    private UserHoldingRepository userHoldingRepository;
    private ETLEnrichmentService etlEnrichmentService;
    private PortfolioAnalyzerService portfolioAnalyzerService;
    private WealthProjectionService wealthProjectionService;
    private ManualSelectionService manualSelectionService;

    @BeforeEach
    void setUp() {
        onboardingService = mock(IOnboardingService.class);
        fundQueryService = mock(FundQueryService.class);
        fundUpsertService = mock(FundUpsertService.class);
        userHoldingRepository = mock(UserHoldingRepository.class);
        etlEnrichmentService = mock(ETLEnrichmentService.class);
        portfolioAnalyzerService = mock(PortfolioAnalyzerService.class);
        wealthProjectionService = mock(WealthProjectionService.class);

        manualSelectionService = new ManualSelectionService(
                onboardingService,
                fundQueryService,
                fundUpsertService,
                userHoldingRepository,
                etlEnrichmentService,
                portfolioAnalyzerService,
                wealthProjectionService);

        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("manual-selection@example.com")
                .build();
        when(onboardingService.getCurrentUser()).thenReturn(user);

        doNothing().when(userHoldingRepository).deleteByUser_UserId(any(UUID.class));
        doNothing().when(userHoldingRepository).flush();
        when(userHoldingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        when(portfolioAnalyzerService.analyzePortfolio(anyList(), anyMap()))
                .thenReturn(PortfolioHealthDTO.builder()
                        .aggregateSectorAllocation(Map.of())
                        .diversificationScore(8.5)
                        .overlapStatus("Low")
                        .sectorConcentration("Balanced")
                        .build());
        when(wealthProjectionService.calculateProjection(anyList(), anyMap(), anyDouble(), anyInt()))
                .thenReturn(WealthProjectionDTO.builder()
                        .projectedYears(10)
                        .totalInvestment(100000.0)
                        .likelyScenarioAmount(180000.0)
                        .timeline(List.of())
                        .build());
    }

    @Test
    void replaceHoldingsUsesFreshCompleteDbFundForIsinRequests() throws Exception {
        Fund fund = buildFund();
        when(fundQueryService.findByIsin("INF879O01027")).thenReturn(Optional.of(fund));

        ManualSelectionResponse response = manualSelectionService
                .replaceHoldingsWithManualSelection(singleSelection("INF879O01027", fund.getFundName()));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("RESOLVED_FROM_DB");
        verify(etlEnrichmentService, never()).enrichPortfolioData(anyList(), any(UUID.class), anyString());
    }

    @Test
    void replaceHoldingsUsesFreshCompleteDbFundForUuidRequests() throws Exception {
        Fund fund = buildFund();
        when(fundQueryService.findById(fund.getFundId())).thenReturn(Optional.of(fund));

        ManualSelectionResponse response = manualSelectionService
                .replaceHoldingsWithManualSelection(singleSelection(fund.getFundId().toString(), fund.getFundName()));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("RESOLVED_FROM_DB");
        verify(etlEnrichmentService, never()).enrichPortfolioData(anyList(), any(UUID.class), anyString());
    }

    @Test
    void replaceHoldingsRoutesToEtlWhenCachedFundIsIncomplete() throws Exception {
        Fund fund = buildFund();
        fund.setTopHoldingsJson(objectMapper.createArrayNode());
        when(fundQueryService.findByIsin("INF879O01027")).thenReturn(Optional.of(fund));
        when(etlEnrichmentService.enrichPortfolioData(anyList(), any(UUID.class), anyString()))
                .thenReturn(EnrichmentResult.builder()
                        .enrichedData(List.of())
                        .build());

        ManualSelectionResponse response = manualSelectionService
                .replaceHoldingsWithManualSelection(singleSelection("INF879O01027", fund.getFundName()));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("ENRICHMENT_FAILED");
        verify(etlEnrichmentService).enrichPortfolioData(anyList(), any(UUID.class), anyString());
    }

    @Test
    void replaceHoldingsRoutesToEtlWhenCachedMetadataHasQualityFlags() throws Exception {
        Fund fund = buildFund();
        fund.setFundMetadataJson(objectMapper.readTree(
                "{\"name\":\"Parag Parikh Flexi Cap Fund\",\"data_quality\":{\"quality_flags\":[\"stale_metadata\"],\"missing_fields\":[]}}"));
        when(fundQueryService.findByIsin("INF879O01027")).thenReturn(Optional.of(fund));
        when(etlEnrichmentService.enrichPortfolioData(anyList(), any(UUID.class), anyString()))
                .thenReturn(EnrichmentResult.builder()
                        .enrichedData(List.of())
                        .build());

        ManualSelectionResponse response = manualSelectionService
                .replaceHoldingsWithManualSelection(singleSelection("INF879O01027", fund.getFundName()));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("ENRICHMENT_FAILED");
        verify(etlEnrichmentService).enrichPortfolioData(anyList(), any(UUID.class), anyString());
    }

    @Test
    void replaceHoldingsMarksFundAsFailedWhenEtlReturnsIncompleteData() throws Exception {
        Fund staleFund = buildFund();
        staleFund.setLastUpdated(LocalDateTime.now().minusDays(8));

        Map<String, Object> enrichedMap = Map.of(
                "input_fund_name", staleFund.getFundName(),
                "fund_name", staleFund.getFundName(),
                "isin", staleFund.getIsin(),
                "currentNav", staleFund.getCurrentNav(),
                "navAsOf", "2026-04-24",
                "sectorAllocation", Map.of("Technology", 18.2),
                "topHoldings", List.of(Map.of("securityName", "HDFC Bank Ltd", "weighting", 6.2)),
                "fundMetadata", Map.of(
                        "data_quality", Map.of(
                                "quality_flags", List.of("missing_analytics"),
                                "missing_fields", List.of())));

        when(fundQueryService.findByIsin("INF879O01027")).thenReturn(Optional.of(staleFund));
        when(etlEnrichmentService.enrichPortfolioData(anyList(), any(UUID.class), anyString()))
                .thenReturn(EnrichmentResult.builder()
                        .enrichedData(List.of(enrichedMap))
                        .build());
        when(fundUpsertService.upsertFromEnriched(anyMap(), anyBoolean()))
                .thenThrow(new ServiceUnavailableException("ETL", "incomplete metadata"));

        ManualSelectionResponse response = manualSelectionService
                .replaceHoldingsWithManualSelection(singleSelection("INF879O01027", staleFund.getFundName()));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("ENRICHMENT_FAILED");
        assertThat(response.getPortfolio().getSummary().getTotalHoldings()).isZero();
    }

    private ManualSelectionRequest singleSelection(String fundId, String fundName) {
        return ManualSelectionRequest.builder()
                .selections(List.of(ManualSelectionItemRequest.builder()
                        .fundId(fundId)
                        .fundName(fundName)
                        .weightPct(100)
                        .build()))
                .build();
    }

    private Fund buildFund() throws Exception {
        return Fund.builder()
                .fundId(UUID.randomUUID())
                .fundName("Parag Parikh Flexi Cap Fund - Direct Plan - Growth")
                .isin("INF879O01027")
                .currentNav(90.56)
                .lastUpdated(LocalDateTime.now().minusDays(2))
                .sectorAllocationJson(objectMapper.readTree("{\"Technology\":12.25}"))
                .topHoldingsJson(objectMapper.readTree("[{\"securityName\":\"HDFC Bank Ltd\",\"weighting\":7.95}]"))
                .fundMetadataJson(objectMapper.readTree(
                        "{\"name\":\"Parag Parikh Flexi Cap Fund\",\"data_quality\":{\"quality_flags\":[],\"missing_fields\":[]}}"))
                .build();
    }
}
