package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.dto.FundSimilarityDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.AssetAllocationDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.PortfolioHealthDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskAnalysisDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioToolFamiliesTest {

    @Mock
    private PortfolioToolFacade portfolioToolFacade;

    @Mock
    private PortfolioChatPayloadFactory payloadFactory;

    @Mock
    private FundQueryService fundQueryService;

    private ObjectMapper objectMapper;
    private FundDataTools fundDataTools;
    private FundAnalyticsTools fundAnalyticsTools;
    private RecommendationTools recommendationTools;
    private Fund fundA;
    private Fund fundB;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        PortfolioAiToolSupport support = new PortfolioAiToolSupport(
                portfolioToolFacade,
                payloadFactory,
                fundQueryService,
                objectMapper);
        fundAnalyticsTools = new FundAnalyticsTools(support);
        fundDataTools = new FundDataTools(support, fundAnalyticsTools);
        recommendationTools = new RecommendationTools(support, fundDataTools, fundAnalyticsTools);

        fundA = sampleFund(
                UUID.fromString("65f8b7b6-e359-474f-9bfe-dd79dee23355"),
                "Parag Parikh Flexi Cap Fund",
                """
                        {"financialServices":30.92692,"technology":12.25581,"communicationServices":11.57313,"utilities":8.8058}
                        """,
                """
                        [
                          {"isin":"INE040A01034","securityName":"HDFC Bank Ltd","ticker":"HDFCBANK","sector":"Financial Services","country":"India","weighting":7.95539,"susEsgRiskScore":20.88,"susEsgRiskCategory":"Medium"},
                          {"isin":"INE752E01010","securityName":"Power Grid Corp Of India Ltd","ticker":"532898","sector":"Utilities","country":"India","weighting":7.16482,"susEsgRiskScore":21.93,"susEsgRiskCategory":"Medium"},
                          {"isin":"INE522F01014","securityName":"Coal India Ltd","ticker":"COALINDIA","sector":"Energy","country":"India","weighting":6.10597,"susEsgRiskScore":46.75,"susEsgRiskCategory":"Severe"},
                          {"isin":"US02079K3059","securityName":"Alphabet Inc Class A","ticker":"GOOGL","sector":"Communication Services","country":"United States","weighting":3.99444,"susEsgRiskScore":19.86,"susEsgRiskCategory":"Low"}
                        ]
                        """,
                """
                        {
                          "nav_history":{"2025-12":95.17,"2026-01":93.52,"2026-02":91.95,"2026-03":85.68,"2026-04":90.56},
                          "risk_volatility":{
                            "fund_risk_volatility":{"for3Year":{"beta":0.615,"alpha":5.938,"rSquared":85.797,"sharpeRatio":1.048,"standardDeviation":9.761}},
                            "index_risk_volatility":{"for3Year":{"beta":0.363,"alpha":2.201,"rSquared":9.77,"sharpeRatio":0.47,"standardDeviation":14.629}},
                            "category_risk_volatility":{"for3Year":{"beta":0.964,"alpha":0.479,"rSquared":89.821,"sharpeRatio":0.486,"standardDeviation":14.933}}
                          }
                        }
                        """);

        fundB = sampleFund(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Comparison Fund",
                """
                        {"financialServices":20.0,"technology":15.0,"healthcare":8.0}
                        """,
                """
                        [
                          {"isin":"INE040A01034","securityName":"HDFC Bank Ltd","ticker":"HDFCBANK","sector":"Financial Services","country":"India","weighting":5.0,"susEsgRiskScore":18.0,"susEsgRiskCategory":"Low"},
                          {"isin":"US02079K3059","securityName":"Alphabet Inc Class A","ticker":"GOOGL","sector":"Communication Services","country":"United States","weighting":4.0,"susEsgRiskScore":19.0,"susEsgRiskCategory":"Low"},
                          {"isin":"INE009A01021","securityName":"Infosys Ltd","ticker":"INFY","sector":"Technology","country":"India","weighting":6.0,"susEsgRiskScore":12.0,"susEsgRiskCategory":"Low"}
                        ]
                        """,
                """
                        {
                          "nav_history":{"2025-12":50.0,"2026-01":52.0,"2026-02":53.0,"2026-03":55.0,"2026-04":57.0},
                          "risk_volatility":{
                            "fund_risk_volatility":{"for3Year":{"beta":0.89,"alpha":1.4,"rSquared":81.0,"sharpeRatio":0.71,"standardDeviation":13.9}},
                            "index_risk_volatility":{"for3Year":{"beta":0.70,"alpha":0.5,"rSquared":40.0,"sharpeRatio":0.50,"standardDeviation":14.1}},
                            "category_risk_volatility":{"for3Year":{"beta":0.95,"alpha":0.3,"rSquared":86.0,"sharpeRatio":0.48,"standardDeviation":14.8}}
                          }
                        }
                        """);

        lenient().when(fundQueryService.findById(fundA.getFundId())).thenReturn(Optional.of(fundA));
        lenient().when(fundQueryService.findById(fundB.getFundId())).thenReturn(Optional.of(fundB));
    }

    @Test
    void shouldReturnCompactAndAnalystFundSnapshots() {
        ObjectNode compact = (ObjectNode) fundDataTools.getFundSnapshot(fundA.getFundId().toString(), Optional.empty());
        ObjectNode analyst = (ObjectNode) fundDataTools.getFundSnapshot(fundA.getFundId().toString(), Optional.of("ANALYST"));

        assertThat(compact.path("detailLevel").asText()).isEqualTo("COMPACT");
        assertThat(compact.path("topSectors")).hasSize(4);
        assertThat(analyst.path("detailLevel").asText()).isEqualTo("ANALYST");
        assertThat(analyst.path("composition")).isNotNull();
        assertThat(analyst.path("trend").path("maxDrawdownPct").asDouble()).isLessThan(0.0);
    }

    @Test
    void shouldComputeOverlapAndRiskDeltas() {
        ObjectNode overlap = (ObjectNode) fundAnalyticsTools.computeOverlap(
                fundA.getFundId().toString(),
                fundB.getFundId().toString(),
                Optional.of("ANALYST"));
        ObjectNode risk = (ObjectNode) fundAnalyticsTools.computeRiskDeltas(
                fundA.getFundId().toString(),
                Optional.of("3Y"),
                Optional.empty());

        assertThat(overlap.path("sectorOverlapPct").asDouble()).isGreaterThan(20.0);
        assertThat(overlap.path("holdingOverlapPct").asDouble()).isGreaterThan(5.0);
        assertThat(risk.path("deltas").path("stdevVsBenchmark").asDouble()).isLessThan(0.0);
        assertThat(risk.path("labels").path("riskAdjustedView").asText()).contains("category");
    }

    @Test
    void shouldComputeEsgConcentrationAndSuitabilityFit() {
        RiskProfileResponse profile = RiskProfileResponse.builder()
                .riskProfile(RiskAnalysisDTO.builder().score(35).level("CONSERVATIVE").build())
                .assetAllocation(AssetAllocationDTO.builder().equity(45.0).debt(45.0).gold(10.0).build())
                .portfolioHealth(PortfolioHealthDTO.builder()
                        .diversificationScore(6.5)
                        .fundSimilarities(List.<FundSimilarityDTO>of())
                        .build())
                .build();
        when(portfolioToolFacade.findRiskProfile()).thenReturn(Optional.of(profile));

        ObjectNode esg = (ObjectNode) fundAnalyticsTools.computeWeightedEsgExposure(
                fundA.getFundId().toString(),
                Optional.of("ANALYST"));
        ObjectNode concentration = (ObjectNode) fundAnalyticsTools.computeConcentrationScore(
                fundA.getFundId().toString(),
                Optional.empty());
        ObjectNode suitability = (ObjectNode) recommendationTools.assessSuitabilityFit(
                List.of(fundA.getFundId().toString(), fundB.getFundId().toString()),
                Optional.of("ANALYST"));

        assertThat(esg.path("severeExposurePct").asDouble()).isGreaterThan(6.0);
        assertThat(concentration.path("label").asText()).isIn("LOW", "MODERATE", "HIGH");
        assertThat(suitability.path("funds")).hasSize(2);
        assertThat(suitability.path("funds").get(0).path("riskProfileLevel").asText()).isEqualTo("CONSERVATIVE");
    }

    private Fund sampleFund(UUID fundId, String name, String sectorJson, String holdingsJson, String metadataJson) throws Exception {
        return Fund.builder()
                .fundId(fundId)
                .fundName(name)
                .isin("ISIN-" + fundId)
                .amcName("AMC")
                .fundCategory("Flexi Cap")
                .currentNav(90.56)
                .navAsOf(Date.valueOf("2026-04-24"))
                .lastUpdated(LocalDateTime.now())
                .sectorAllocationJson(objectMapper.readTree(sectorJson))
                .topHoldingsJson(objectMapper.readTree(holdingsJson))
                .fundMetadataJson(objectMapper.readTree(metadataJson))
                .build();
    }
}
