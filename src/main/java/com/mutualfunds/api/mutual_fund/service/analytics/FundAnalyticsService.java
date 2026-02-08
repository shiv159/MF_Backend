package com.mutualfunds.api.mutual_fund.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioCovarianceDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundAnalyticsService {

    private final FundRepository fundRepository;

    private static final DateTimeFormatter NAV_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // ========================================
    // ROLLING RETURNS
    // ========================================

    public RollingReturnsDTO calculateRollingReturns(UUID fundId) {
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new IllegalArgumentException("Fund not found: " + fundId));
        return calculateRollingReturns(fund);
    }

    public RollingReturnsDTO calculateRollingReturns(Fund fund) {
        JsonNode metadata = fund.getFundMetadataJson();
        if (metadata == null) {
            return buildEmptyRollingReturns(fund);
        }

        // Extract NAV history - it's a map of date -> nav value
        JsonNode navHistoryNode = findNavHistory(metadata);
        if (navHistoryNode == null || navHistoryNode.isEmpty()) {
            return buildEmptyRollingReturns(fund);
        }

        // Parse NAV history into sorted map
        TreeMap<LocalDate, Double> navHistory = parseNavHistory(navHistoryNode);
        if (navHistory.isEmpty()) {
            return buildEmptyRollingReturns(fund);
        }

        LocalDate latestDate = navHistory.lastKey();
        Double latestNav = navHistory.get(latestDate);

        return RollingReturnsDTO.builder()
                .fundId(fund.getFundId().toString())
                .fundName(fund.getFundName())
                .return1M(calculateReturn(navHistory, latestDate, latestNav, 1))
                .return3M(calculateReturn(navHistory, latestDate, latestNav, 3))
                .return6M(calculateReturn(navHistory, latestDate, latestNav, 6))
                .return1Y(calculateReturn(navHistory, latestDate, latestNav, 12))
                .return3Y(calculateReturn(navHistory, latestDate, latestNav, 36))
                .return5Y(calculateReturn(navHistory, latestDate, latestNav, 60))
                .sipReturn3Y(calculateSipReturn(navHistory, 36))
                .lumpSumReturn3Y(calculateReturn(navHistory, latestDate, latestNav, 36))
                .cagr(calculateCAGR(navHistory))
                .calculatedAsOf(latestDate)
                .build();
    }

    private JsonNode findNavHistory(JsonNode metadata) {
        // Try different possible locations
        if (metadata.has("nav_history")) {
            return metadata.get("nav_history");
        }
        if (metadata.has("mstarpy_metadata") && metadata.get("mstarpy_metadata").has("nav_history")) {
            return metadata.get("mstarpy_metadata").get("nav_history");
        }
        return null;
    }

    private TreeMap<LocalDate, Double> parseNavHistory(JsonNode navHistoryNode) {
        TreeMap<LocalDate, Double> navHistory = new TreeMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = navHistoryNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                LocalDate date = parseDate(entry.getKey());
                Double nav = entry.getValue().asDouble();
                if (date != null && nav > 0) {
                    navHistory.put(date, nav);
                }
            } catch (Exception e) {
                log.trace("Skipping invalid NAV entry: {}", entry.getKey());
            }
        }

        return navHistory;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            // Try YYYY-MM format first (from mftool aggregated monthly data)
            if (dateStr.matches("\\d{4}-\\d{2}")) {
                return LocalDate.parse(dateStr + "-01"); // Append day to make it valid
            }
            // Try dd-MM-yyyy format
            return LocalDate.parse(dateStr, NAV_DATE_FORMAT);
        } catch (Exception e1) {
            try {
                // Try yyyy-MM-dd format
                return LocalDate.parse(dateStr);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private Double calculateReturn(TreeMap<LocalDate, Double> navHistory,
            LocalDate latestDate, Double latestNav, int months) {
        LocalDate targetDate = latestDate.minusMonths(months);

        // Find closest date to target
        Map.Entry<LocalDate, Double> closestEntry = navHistory.floorEntry(targetDate);
        if (closestEntry == null) {
            closestEntry = navHistory.ceilingEntry(targetDate);
        }

        if (closestEntry == null) {
            return null;
        }

        Double pastNav = closestEntry.getValue();
        if (pastNav <= 0) {
            return null;
        }

        // Simple return percentage
        double simpleReturn = ((latestNav - pastNav) / pastNav) * 100;
        return Math.round(simpleReturn * 100.0) / 100.0;
    }

    private Double calculateSipReturn(TreeMap<LocalDate, Double> navHistory, int months) {
        if (navHistory.size() < 12) {
            return null;
        }

        LocalDate endDate = navHistory.lastKey();
        LocalDate startDate = endDate.minusMonths(months);

        // Get monthly NAVs for SIP calculation
        List<Double> monthlyNavs = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            Map.Entry<LocalDate, Double> entry = navHistory.floorEntry(currentDate);
            if (entry != null) {
                monthlyNavs.add(entry.getValue());
            }
            currentDate = currentDate.plusMonths(1);
        }

        if (monthlyNavs.size() < 12) {
            return null;
        }

        // Calculate SIP return (assuming ₹1000 monthly investment)
        double monthlyInvestment = 1000.0;
        double totalUnits = 0.0;
        double totalInvested = 0.0;

        for (Double nav : monthlyNavs) {
            if (nav > 0) {
                totalUnits += monthlyInvestment / nav;
                totalInvested += monthlyInvestment;
            }
        }

        Double latestNav = navHistory.get(endDate);
        if (latestNav == null || latestNav <= 0 || totalUnits <= 0) {
            return null;
        }

        double currentValue = totalUnits * latestNav;
        double sipReturn = ((currentValue - totalInvested) / totalInvested) * 100;

        return Math.round(sipReturn * 100.0) / 100.0;
    }

    private Double calculateCAGR(TreeMap<LocalDate, Double> navHistory) {
        if (navHistory.size() < 2) {
            return null;
        }

        LocalDate startDate = navHistory.firstKey();
        LocalDate endDate = navHistory.lastKey();
        Double startNav = navHistory.get(startDate);
        Double endNav = navHistory.get(endDate);

        if (startNav == null || endNav == null || startNav <= 0) {
            return null;
        }

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        double years = daysBetween / 365.25;

        if (years < 1) {
            return null;
        }

        double cagr = (Math.pow(endNav / startNav, 1.0 / years) - 1) * 100;
        return Math.round(cagr * 100.0) / 100.0;
    }

    private RollingReturnsDTO buildEmptyRollingReturns(Fund fund) {
        return RollingReturnsDTO.builder()
                .fundId(fund.getFundId().toString())
                .fundName(fund.getFundName())
                .calculatedAsOf(LocalDate.now())
                .build();
    }

    // ========================================
    // RISK INSIGHTS
    // ========================================

    public RiskInsightsDTO calculateRiskInsights(UUID fundId) {
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new IllegalArgumentException("Fund not found: " + fundId));
        return calculateRiskInsights(fund);
    }

    public RiskInsightsDTO calculateRiskInsights(Fund fund) {
        JsonNode metadata = fund.getFundMetadataJson();

        Double alpha = extractMetric(metadata, "alpha");
        Double beta = extractMetric(metadata, "beta");
        Double sharpeRatio = extractMetric(metadata, "sharpe_ratio", "sharpeRatio");
        Double stdDev = extractMetric(metadata, "stdev", "standardDeviation", "std_dev");

        return RiskInsightsDTO.builder()
                .fundId(fund.getFundId().toString())
                .fundName(fund.getFundName())
                .alpha(alpha)
                .beta(beta)
                .sharpeRatio(sharpeRatio)
                .standardDeviation(stdDev)
                .alphaInsight(generateAlphaInsight(alpha))
                .betaInsight(generateBetaInsight(beta))
                .volatilityLevel(determineVolatilityLevel(beta, stdDev))
                .overallRiskLabel(generateRiskLabel(beta, stdDev, alpha))
                .build();
    }

    private Double extractMetric(JsonNode metadata, String... keys) {
        if (metadata == null) {
            return null;
        }

        for (String key : keys) {
            // Check root level
            if (metadata.has(key) && !metadata.get(key).isNull()) {
                return metadata.get(key).asDouble();
            }

            // Check mstarpy_metadata
            if (metadata.has("mstarpy_metadata")) {
                JsonNode mstar = metadata.get("mstarpy_metadata");
                if (mstar.has(key) && !mstar.get(key).isNull()) {
                    return mstar.get(key).asDouble();
                }
            }

            // Check risk_volatility nested structure
            JsonNode riskVol = metadata.at("/risk_volatility/fund_risk_volatility/for3Year");
            if (!riskVol.isMissingNode() && riskVol.has(key) && !riskVol.get(key).isNull()) {
                return riskVol.get(key).asDouble();
            }
        }

        return null;
    }

    private String generateAlphaInsight(Double alpha) {
        if (alpha == null) {
            return "Alpha data not available";
        }

        if (alpha > 2) {
            return String.format("✅ Outperforms benchmark by %.1f%% annually", alpha);
        } else if (alpha > 0) {
            return String.format("Slightly beats benchmark by %.1f%% annually", alpha);
        } else if (alpha > -2) {
            return String.format("Slightly trails benchmark by %.1f%% annually", Math.abs(alpha));
        } else {
            return String.format("⚠️ Underperforms benchmark by %.1f%% annually", Math.abs(alpha));
        }
    }

    private String generateBetaInsight(Double beta) {
        if (beta == null) {
            return "Beta data not available";
        }

        if (beta < 0.8) {
            return "🛡️ Lower volatility than market - defensive";
        } else if (beta <= 1.2) {
            return "📊 Market-aligned volatility";
        } else {
            return "⚡ Higher volatility than market - aggressive";
        }
    }

    private String determineVolatilityLevel(Double beta, Double stdDev) {
        if (beta != null) {
            if (beta < 0.8)
                return "LOW";
            if (beta > 1.2)
                return "HIGH";
            return "MARKET_ALIGNED";
        }

        if (stdDev != null) {
            if (stdDev < 12)
                return "LOW";
            if (stdDev > 20)
                return "HIGH";
            return "MARKET_ALIGNED";
        }

        return "MARKET_ALIGNED";
    }

    private String generateRiskLabel(Double beta, Double stdDev, Double alpha) {
        String volatilityLevel = determineVolatilityLevel(beta, stdDev);

        return switch (volatilityLevel) {
            case "LOW" -> "🛡️ Defensive";
            case "HIGH" -> "⚡ Aggressive";
            default -> "📊 Balanced";
        };
    }

    // ========================================
    // PORTFOLIO COVARIANCE
    // ========================================

    public PortfolioCovarianceDTO calculatePortfolioCovariance(List<UUID> fundIds, Map<UUID, Double> weights) {
        List<Fund> funds = fundRepository.findAllById(fundIds);
        if (funds.isEmpty()) {
            throw new IllegalArgumentException("No funds found");
        }

        return calculatePortfolioCovariance(funds, weights);
    }

    public PortfolioCovarianceDTO calculatePortfolioCovariance(List<Fund> funds, Map<UUID, Double> weights) {
        int n = funds.size();

        // Extract monthly returns for each fund
        Map<UUID, List<Double>> fundReturns = new LinkedHashMap<>();
        int minMonths = Integer.MAX_VALUE;

        for (Fund fund : funds) {
            List<Double> returns = extractMonthlyReturns(fund);
            fundReturns.put(fund.getFundId(), returns);
            if (!returns.isEmpty()) {
                minMonths = Math.min(minMonths, returns.size());
            }
        }

        // Align returns to same length (use most recent N months)
        List<UUID> orderedIds = new ArrayList<>(fundReturns.keySet());
        double[][] alignedReturns = new double[n][];

        for (int i = 0; i < n; i++) {
            List<Double> returns = fundReturns.get(orderedIds.get(i));
            if (returns.isEmpty()) {
                alignedReturns[i] = new double[0];
            } else {
                int startIdx = Math.max(0, returns.size() - minMonths);
                alignedReturns[i] = returns.subList(startIdx, returns.size())
                        .stream().mapToDouble(Double::doubleValue).toArray();
            }
        }

        // Calculate covariance and correlation matrices
        double[][] covMatrix = new double[n][n];
        double[][] corrMatrix = new double[n][n];
        double[] stdDevs = new double[n];

        // Calculate means and std devs
        double[] means = new double[n];
        for (int i = 0; i < n; i++) {
            if (alignedReturns[i].length > 0) {
                means[i] = Arrays.stream(alignedReturns[i]).average().orElse(0);
                double variance = Arrays.stream(alignedReturns[i])
                        .map(r -> Math.pow(r - means[i], 2))
                        .average().orElse(0);
                stdDevs[i] = Math.sqrt(variance);
            }
        }

        // Calculate covariance matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (alignedReturns[i].length > 0 && alignedReturns[j].length > 0) {
                    double cov = 0;
                    int len = Math.min(alignedReturns[i].length, alignedReturns[j].length);
                    for (int k = 0; k < len; k++) {
                        cov += (alignedReturns[i][k] - means[i]) * (alignedReturns[j][k] - means[j]);
                    }
                    covMatrix[i][j] = cov / len;

                    // Correlation
                    if (stdDevs[i] > 0 && stdDevs[j] > 0) {
                        corrMatrix[i][j] = covMatrix[i][j] / (stdDevs[i] * stdDevs[j]);
                    } else {
                        corrMatrix[i][j] = i == j ? 1.0 : 0.0;
                    }
                } else {
                    covMatrix[i][j] = 0;
                    corrMatrix[i][j] = i == j ? 1.0 : 0.0;
                }
            }
        }

        // Calculate portfolio variance: w' * Σ * w
        double portfolioVariance = 0;
        double weightedAvgStdDev = 0;

        for (int i = 0; i < n; i++) {
            double wi = weights.getOrDefault(orderedIds.get(i), 0.0);
            weightedAvgStdDev += wi * stdDevs[i];

            for (int j = 0; j < n; j++) {
                double wj = weights.getOrDefault(orderedIds.get(j), 0.0);
                portfolioVariance += wi * wj * covMatrix[i][j];
            }
        }

        double portfolioStdDev = Math.sqrt(portfolioVariance);
        double diversificationBenefit = weightedAvgStdDev > 0
                ? ((weightedAvgStdDev - portfolioStdDev) / weightedAvgStdDev) * 100
                : 0;

        List<String> fundNames = funds.stream().map(Fund::getFundName).toList();
        List<String> fundIdStrings = orderedIds.stream().map(UUID::toString).toList();

        return PortfolioCovarianceDTO.builder()
                .fundIds(fundIdStrings)
                .fundNames(fundNames)
                .covarianceMatrix(roundMatrix(covMatrix))
                .correlationMatrix(roundMatrix(corrMatrix))
                .portfolioVariance(round(portfolioVariance))
                .portfolioStdDev(round(portfolioStdDev * 100)) // Convert to percentage
                .weightedAvgStdDev(round(weightedAvgStdDev * 100))
                .diversificationBenefit(round(diversificationBenefit))
                .calculationMethod("HISTORICAL_MONTHLY")
                .monthsUsed(minMonths == Integer.MAX_VALUE ? 0 : minMonths)
                .build();
    }

    private List<Double> extractMonthlyReturns(Fund fund) {
        JsonNode metadata = fund.getFundMetadataJson();
        JsonNode navHistoryNode = findNavHistory(metadata);

        if (navHistoryNode == null || navHistoryNode.isEmpty()) {
            return Collections.emptyList();
        }

        TreeMap<LocalDate, Double> navHistory = parseNavHistory(navHistoryNode);
        if (navHistory.size() < 2) {
            return Collections.emptyList();
        }

        // Get monthly NAVs (last day of each month)
        TreeMap<String, Double> monthlyNavs = new TreeMap<>();
        for (Map.Entry<LocalDate, Double> entry : navHistory.entrySet()) {
            String monthKey = entry.getKey().getYear() + "-" +
                    String.format("%02d", entry.getKey().getMonthValue());
            monthlyNavs.put(monthKey, entry.getValue()); // Last entry for month wins
        }

        // Calculate monthly returns
        List<Double> returns = new ArrayList<>();
        Double previousNav = null;

        for (Double nav : monthlyNavs.values()) {
            if (previousNav != null && previousNav > 0) {
                double monthlyReturn = (nav - previousNav) / previousNav;
                returns.add(monthlyReturn);
            }
            previousNav = nav;
        }

        return returns;
    }

    private double[][] roundMatrix(double[][] matrix) {
        double[][] rounded = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            rounded[i] = new double[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                rounded[i][j] = Math.round(matrix[i][j] * 10000.0) / 10000.0;
            }
        }
        return rounded;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
