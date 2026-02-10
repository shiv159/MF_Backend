package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured portfolio diagnostic response.
 * Contains AI/template summary, rule-based suggestions, strengths, and raw
 * metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDiagnosticDTO {

    /** 2-4 sentence portfolio summary (AI-generated or template-based) */
    private String summary;

    /** Actionable suggestions based on rule-based analysis */
    private List<DiagnosticSuggestion> suggestions;

    /** 2-3 positive highlights about the portfolio */
    private List<String> strengths;

    /** Raw metrics backing the analysis */
    private DiagnosticMetrics metrics;

    // ─── Inner Classes ───────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosticSuggestion {

        /** Category of the issue detected */
        private SuggestionCategory category;

        /** Severity: HIGH, MEDIUM, LOW */
        private Severity severity;

        /** Human-readable actionable advice */
        private String message;

        /** Supporting data (e.g., which AMCs are concentrated) */
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosticMetrics {

        /** Total number of unique funds */
        private int totalFunds;

        /** Fund house (AMC) distribution: AMC name -> count */
        private Map<String, Integer> fundHouseDistribution;

        /** Asset class breakdown: category -> allocation percentage */
        private Map<String, Double> assetClassBreakdown;

        /** Diversification score from PortfolioAnalyzerService (0-10) */
        private double diversificationScore;

        /** Overlap status: Low, Moderate, High */
        private String overlapStatus;

        /** Sector concentration status: Balanced or High */
        private String sectorConcentration;

        /** Top sector and its allocation percentage */
        private String topSector;
        private double topSectorAllocation;
    }

    public enum SuggestionCategory {
        FUND_HOUSE_CONCENTRATION,
        LACK_OF_DIVERSIFICATION,
        OVER_DIVERSIFICATION,
        SECTOR_CONCENTRATION,
        STOCK_OVERLAP,
        EMOTIONAL_DECISIONS,
        HIGH_EXPENSE_RATIO,
        NO_DEBT_ALLOCATION,
        NO_EQUITY_ALLOCATION,
        MARKET_CAP_IMBALANCE
    }

    public enum Severity {
        HIGH, MEDIUM, LOW
    }
}
