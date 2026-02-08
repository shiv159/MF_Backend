package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskInsightsDTO {
    private String fundId;
    private String fundName;
    
    // Raw metrics
    private Double alpha;
    private Double beta;
    private Double sharpeRatio;
    private Double standardDeviation;
    
    // Plain language insights
    private String alphaInsight;      // "Beats benchmark by 2.5% annually"
    private String betaInsight;       // "Lower volatility than market"
    private String volatilityLevel;   // "LOW" | "MARKET_ALIGNED" | "HIGH"
    private String overallRiskLabel;  // "🛡️ Defensive" | "📊 Balanced" | "⚡ Aggressive"
}
