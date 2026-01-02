package com.mutualfunds.api.mutual_fund.ai.model;

// Simplified placeholder for 1.0.0-M1 compatibility
// Real implementation will depend on how we inject context (likely via PromptTemplate)

public class PortfolioContextAdvisor {

    private final String userId;

    public PortfolioContextAdvisor(String userId) {
        this.userId = userId;
    }

    public String getContext() {
        return "{ \"holdings\": \"User has 50 shares of TSLA\" }";
    }
}
