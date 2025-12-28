package com.mutualfunds.api.mutual_fund.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.dto.analytics.WealthProjectionDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.YearProjection;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WealthProjectionService {

    private final ObjectMapper objectMapper;

    // Simulation Constants
    private static final int SIMULATION_RUNS = 1000;
    private static final double RISK_FREE_RATE = 0.05; // 5% base rate proxy

    public WealthProjectionDTO calculateProjection(List<Fund> funds, Map<UUID, Double> weights, double investmentAmount,
            int years) {
        log.info("Calculating wealth projection for portfolio value: {}", investmentAmount);

        // 1. Calculate Portfolio Characteristics
        var stats = calculatePortfolioStats(funds, weights);
        double portfolioMeanReturn = stats.meanReturn;
        double portfolioStdDev = stats.stdDev;

        if (portfolioStdDev == 0.0) {
            // Fallback if no history: Use Category Averages or Conservative defaults
            portfolioMeanReturn = 0.12; // 12% default
            portfolioStdDev = 0.15; // 15% default
        }

        log.info("Portfolio Stats - Mean: {}%, StdDev: {}%", portfolioMeanReturn * 100, portfolioStdDev * 100);

        // 2. Monte Carlo Simulation
        return runMonteCarloSimulation(investmentAmount, years, portfolioMeanReturn, portfolioStdDev);
    }

    private WealthProjectionDTO runMonteCarloSimulation(double initialAmount, int years, double meanAnnualReturn,
            double annualStdDev) {
        Random random = new Random();
        NormalDistribution normalDistribution = new NormalDistribution(0, 1); // Standard Normal

        // Storage for all paths: paths[year][simulation_index]
        double[][] paths = new double[years + 1][SIMULATION_RUNS];

        // Initialize Year 0
        for (int i = 0; i < SIMULATION_RUNS; i++) {
            paths[0][i] = initialAmount;
        }

        // Run Simulations
        for (int i = 0; i < SIMULATION_RUNS; i++) {
            for (int year = 1; year <= years; year++) {
                // Geometric Brownian Motion Logic
                // Return = Mean - 0.5*Var + StdDev * RandomZ
                double randomZ = normalDistribution.sample();
                double annualReturn = meanAnnualReturn - (0.5 * Math.pow(annualStdDev, 2)) + (annualStdDev * randomZ);

                paths[year][i] = paths[year - 1][i] * Math.exp(annualReturn);
            }
        }

        // 3. Extract Percentiles
        List<YearProjection> timeline = new ArrayList<>();
        double finalOptimistic = 0;
        double finalExpected = 0;
        double finalPessimistic = 0;

        for (int year = 1; year <= years; year++) {
            double[] yearValues = paths[year];
            Arrays.sort(yearValues);

            double pessimistic = yearValues[(int) (SIMULATION_RUNS * 0.10)]; // 10th percentile
            double expected = yearValues[(int) (SIMULATION_RUNS * 0.50)]; // Median
            double optimistic = yearValues[(int) (SIMULATION_RUNS * 0.90)]; // 90th percentile

            timeline.add(YearProjection.builder()
                    .year(year)
                    .pessimisticAmount(Math.round(pessimistic))
                    .expectedAmount(Math.round(expected))
                    .optimisticAmount(Math.round(optimistic))
                    .build());

            if (year == years) {
                finalPessimistic = pessimistic;
                finalExpected = expected;
                finalOptimistic = optimistic;
            }
        }

        return WealthProjectionDTO.builder()
                .projectedYears(years)
                .totalInvestment(initialAmount)
                .likelyScenarioAmount(Math.round(finalExpected * 100.0) / 100.0)
                .pessimisticScenarioAmount(Math.round(finalPessimistic * 100.0) / 100.0)
                .optimisticScenarioAmount(Math.round(finalOptimistic * 100.0) / 100.0)
                .timeline(timeline)
                .build();
    }

    private record PortfolioStats(double meanReturn, double stdDev) {
    }

    private PortfolioStats calculatePortfolioStats(List<Fund> funds, Map<UUID, Double> weights) {
        double weightedMeanReturn = 0.0;
        double weightedVariance = 0.0; // Simplified assumption: Correlation = 1 (worst case) or calculating real
                                       // covariance requires alignment

        // FOR NOW: We calculate weighted average risk/return.
        // In "Real Covariance" step (Goal 2), we would align date-wise.

        for (Fund fund : funds) {
            double w = weights.getOrDefault(fund.getFundId(), 0.0);
            if (w <= 0.001)
                continue;

            var fundStats = extractFundStats(fund);
            weightedMeanReturn += fundStats.meanReturn * w;

            // Simplified risk calculation (Sum of weighted risks - assumes high
            // correlation, conservative)
            // Ideally: Sqrt(w1^2*s1^2 + w2^2*s2^2 + 2*w1*w2*cov...)
            // Since we are doing "Proxy Monte Carlo", weighted average StdDev is a common
            // approximation for retail apps
            // unless we build the full covariance matrix.
            weightedVariance += fundStats.stdDev * w;
        }

        return new PortfolioStats(weightedMeanReturn, weightedVariance);
    }

    private PortfolioStats extractFundStats(Fund fund) {
        // Try simple metadata first
        try {
            JsonNode meta = fund.getFundMetadataJson();
            if (meta != null && meta.has("mstarpy_metadata")) {
                JsonNode mstar = meta.get("mstarpy_metadata");

                // Risk (StdDev)
                double stdDev = 0.15; // default
                if (mstar.has("stdev") && !mstar.get("stdev").isNull()) {
                    stdDev = mstar.get("stdev").asDouble() / 100.0;
                } else if (mstar.has("risk_volatility")) {
                    // Dig deeper into risk_volatility -> for3Year
                    JsonNode riskVol = mstar.at("/risk_volatility/fund_risk_volatility/for3Year");
                    if (!riskVol.isMissingNode() && riskVol.has("standardDeviation")) {
                        stdDev = riskVol.get("standardDeviation").asDouble() / 100.0;
                    }
                }

                // Return (Alpha + Benchmark) or CAGR
                // We will approximate Mean Return = Risk Free + Alpha +
                // Beta*(MarketRiskPremium)
                // Or simply use Category Average.
                // Let's use Alpha-adjusted Market proxy.
                double alpha = 0.0;
                double beta = 1.0;

                if (mstar.has("alpha"))
                    alpha = mstar.get("alpha").asDouble() / 100.0;
                if (mstar.has("beta"))
                    beta = mstar.get("beta").asDouble();

                double marketReturn = 0.12; // Nifty long term avg
                double fundReturn = RISK_FREE_RATE + beta * (marketReturn - RISK_FREE_RATE) + alpha;

                return new PortfolioStats(fundReturn, stdDev);
            }
        } catch (Exception e) {
            log.warn("Failed to extract stats for fund {}: {}", fund.getFundName(), e.getMessage());
        }

        return new PortfolioStats(0.12, 0.15); // Fallback to 12% Return, 15% Risk
    }
}
