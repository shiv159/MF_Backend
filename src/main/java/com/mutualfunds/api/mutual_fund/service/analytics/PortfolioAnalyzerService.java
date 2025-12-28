package com.mutualfunds.api.mutual_fund.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;

import com.mutualfunds.api.mutual_fund.dto.risk.PortfolioHealthDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.StockOverviewDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.FundSimilarityDTO;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalyzerService {

    /**
     * Analyze a portfolio of funds with given weights.
     * 
     * @param funds      List of Fund entities
     * @param devWeights Map of FundID -> Weight (0.0 to 1.0)
     * @return Health Report
     */
    public PortfolioHealthDTO analyzePortfolio(List<Fund> funds, Map<UUID, Double> weights) {
        log.info("Analyzing portfolio with {} funds", funds.size());

        Map<String, StockAggregator> stockMap = new HashMap<>(); // Key: ISIN
        Map<String, Double> sectorMap = new HashMap<>();

        for (Fund fund : funds) {
            double fundWeight = weights.getOrDefault(fund.getFundId(), 0.0);
            if (fundWeight <= 0.0001)
                continue;

            // 1. Process Stocks
            processStocks(fund, fundWeight, stockMap);

            // 2. Process Sectors
            processSectors(fund, fundWeight, sectorMap);
        }

        // 3. Aggregate Results
        List<StockOverviewDTO> overlaps = stockMap.values().stream()
                .filter(s -> s.count > 1 && s.totalWeight > 1.0) // Significant overlap only
                .map(s -> StockOverviewDTO.builder()
                        .isin(s.isin)
                        .stockName(s.name)
                        .totalWeight(Math.round(s.totalWeight * 100.0) / 100.0)
                        .fundCount(s.count)
                        .fundNames(s.funds)
                        .build())
                .sorted(Comparator.comparingDouble(StockOverviewDTO::getTotalWeight).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Sort sectors
        Map<String, Double> sortedSectors = sectorMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Math.round(e.getValue() * 100.0) / 100.0,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        // 4. Calculate Score
        double topSectorConcentration = sortedSectors.values().stream().findFirst().orElse(0.0);
        double overlapPenalty = overlaps.stream().mapToDouble(StockOverviewDTO::getTotalWeight).sum() / 5.0; // Penalty
        double diversificationScore = Math.max(0,
                Math.min(10, 10.0 - (topSectorConcentration / 10.0) - overlapPenalty));

        String overlapStatus = overlaps.isEmpty() ? "Low"
                : (overlaps.get(0).getTotalWeight() > 5.0 ? "High" : "Moderate");

        // ... inside analyzePortfolio method ...

        // 5. Calculate Fund Similarities (Pairwise)
        List<FundSimilarityDTO> similarities = calculateFundSimilarities(funds);

        return PortfolioHealthDTO.builder()
                .sectorConcentration(topSectorConcentration > 30.0 ? "High" : "Balanced")
                .overlapStatus(overlapStatus)
                .diversificationScore(Math.round(diversificationScore * 10.0) / 10.0) // 1 decimal
                .topOverlappingStocks(overlaps)
                .aggregateSectorAllocation(sortedSectors)
                .fundSimilarities(similarities)
                .build();
    }

    private List<FundSimilarityDTO> calculateFundSimilarities(List<Fund> funds) {
        List<FundSimilarityDTO> result = new ArrayList<>();
        int n = funds.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Fund f1 = funds.get(i);
                Fund f2 = funds.get(j);

                double stockOverlap = calculateStockOverlap(f1, f2);
                double sectorCorr = calculateSectorCorrelation(f1, f2);

                if (stockOverlap > 10.0 || sectorCorr > 70.0) { // Only return meaningful overlaps
                    result.add(FundSimilarityDTO.builder()
                            .fundA(f1.getFundName())
                            .fundB(f2.getFundName())
                            .stockOverlapPct(Math.round(stockOverlap * 100.0) / 100.0)
                            .sectorCorrelation(Math.round(sectorCorr * 100.0) / 100.0)
                            .build());
                }
            }
        }
        result.sort(Comparator.comparingDouble(FundSimilarityDTO::getStockOverlapPct).reversed());
        return result;
    }

    private double calculateStockOverlap(Fund f1, Fund f2) {
        Map<String, Double> stocks1 = extractStockWeights(f1); // ISIN -> Weight
        Map<String, Double> stocks2 = extractStockWeights(f2);

        double overlap = 0.0;
        for (Map.Entry<String, Double> entry : stocks1.entrySet()) {
            if (stocks2.containsKey(entry.getKey())) {
                overlap += Math.min(entry.getValue(), stocks2.get(entry.getKey()));
            }
        }
        return overlap;
    }

    private double calculateSectorCorrelation(Fund f1, Fund f2) {
        Map<String, Double> s1 = extractSectorWeights(f1);
        Map<String, Double> s2 = extractSectorWeights(f2);

        // Using Overlap Coefficient for Sectors as well (easier to understand than
        // Cosine)
        double overlap = 0.0;
        for (Map.Entry<String, Double> entry : s1.entrySet()) {
            String sector = entry.getKey();
            double w1 = entry.getValue();
            double w2 = s2.getOrDefault(sector, 0.0);
            overlap += Math.min(w1, w2);
        }
        return overlap;
    }

    private Map<String, Double> extractStockWeights(Fund fund) {
        Map<String, Double> weights = new HashMap<>();
        if (fund.getTopHoldingsJson() != null && fund.getTopHoldingsJson().isArray()) {
            for (JsonNode node : fund.getTopHoldingsJson()) {
                String isin = node.path("isin").asText();
                double w = node.path("weighting").asDouble();
                if (isin != null && !isin.isEmpty())
                    weights.put(isin, w);
            }
        }
        return weights;
    }

    private Map<String, Double> extractSectorWeights(Fund fund) {
        Map<String, Double> weights = new HashMap<>();
        if (fund.getSectorAllocationJson() != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = fund.getSectorAllocationJson().fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                weights.put(field.getKey(), field.getValue().asDouble());
            }
        }
        return weights;
    }

    // Helper for aggregation
    private static class StockAggregator {
        String name;
        String isin;
        double totalWeight = 0.0;
        int count = 0;
        List<String> funds = new ArrayList<>();
    }

    private void processStocks(Fund fund, double fundWeight, Map<String, StockAggregator> stockMap) {
        if (fund.getTopHoldingsJson() == null)
            return;

        try {
            if (fund.getTopHoldingsJson().isArray()) {
                for (JsonNode stockNode : fund.getTopHoldingsJson()) {
                    String isin = stockNode.path("isin").asText();
                    String name = stockNode.path("securityName").asText();
                    double stockFundWeight = stockNode.path("weighting").asDouble(); // e.g., 5.0 for 5%

                    if (isin == null || isin.isEmpty())
                        continue;

                    // Calculate exposure contribution from this fund (approximate since weights are
                    // relative)
                    // We sum the raw weights to show "Total Portfolio Exposure" approx
                    double contribution = stockFundWeight * fundWeight / 100.0;

                    StockAggregator agg = stockMap.computeIfAbsent(isin, k -> new StockAggregator());
                    agg.isin = isin;
                    agg.name = name;
                    agg.totalWeight += contribution * 100.0; // Keep as percentage
                    agg.count++;
                    agg.funds.add(fund.getFundName());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing stocks for fund {}", fund.getFundName(), e);
        }
    }

    // Simplification: Using Ref object for cleaner aggregation instead of Builder
    // in loop
    private void processSectors(Fund fund, double fundWeight, Map<String, Double> sectorMap) {
        if (fund.getSectorAllocationJson() == null)
            return;

        try {
            Iterator<Map.Entry<String, JsonNode>> fields = fund.getSectorAllocationJson().fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String sector = field.getKey();
                double allocation = field.getValue().asDouble();

                double weightedAlloc = allocation * fundWeight;
                sectorMap.merge(capitalise(sector), weightedAlloc, Double::sum);
            }
        } catch (Exception e) {
            log.error("Error parsing sectors for fund {}", fund.getFundName(), e);
        }
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
