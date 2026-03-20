package com.mutualfunds.api.mutual_fund.features.portfolio.quality.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PortfolioDataQualityInspector {

    public record FundIssue(String fundId, String fundName, List<String> flags) {
    }

    public record Result(
            int staleCount,
            int missingCount,
            int freshCount,
            List<String> warnings,
            List<FundIssue> staleFunds,
            List<FundIssue> missingFunds) {
    }

    public Result inspect(List<UserHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return new Result(0, 0, 0, List.of("No saved holdings are available yet."), List.of(), List.of());
        }

        List<String> warnings = new ArrayList<>();
        List<FundIssue> staleFunds = new ArrayList<>();
        List<FundIssue> missingFunds = new ArrayList<>();
        int freshCount = 0;

        for (UserHolding holding : holdings) {
            Fund fund = holding.getFund();
            if (fund == null) {
                continue;
            }

            List<String> staleFlags = new ArrayList<>();
            if (fund.getCurrentNav() == null) {
                staleFlags.add("missing_nav");
            }
            if (fund.getLastUpdated() == null || fund.getLastUpdated().isBefore(LocalDateTime.now().minusDays(7))) {
                staleFlags.add("stale_metadata");
            }

            if (!staleFlags.isEmpty()) {
                staleFunds.add(new FundIssue(fund.getFundId().toString(), fund.getFundName(), staleFlags));
                warnings.add(String.format("%s has stale or incomplete pricing data.", fund.getFundName()));
            } else {
                freshCount++;
            }

            List<String> missingFlags = new ArrayList<>();
            if (isMissing(fund.getSectorAllocationJson())) {
                missingFlags.add("missing_sector_allocation");
            }
            if (isMissing(fund.getTopHoldingsJson())) {
                missingFlags.add("missing_top_holdings");
            }
            if (isMissing(fund.getFundMetadataJson())) {
                missingFlags.add("missing_fund_metadata");
            } else {
                missingFlags.addAll(readQualityFlags(fund.getFundMetadataJson()));
            }

            if (!missingFlags.isEmpty()) {
                missingFunds.add(new FundIssue(fund.getFundId().toString(), fund.getFundName(), missingFlags));
                warnings.add(String.format("%s is missing enrichment fields: %s.", fund.getFundName(),
                        String.join(", ", missingFlags)));
            }
        }

        return new Result(
                staleFunds.size(),
                missingFunds.size(),
                freshCount,
                warnings.stream().distinct().limit(5).toList(),
                staleFunds,
                missingFunds);
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isNull() || node.isEmpty();
    }

    private List<String> readQualityFlags(JsonNode metadata) {
        JsonNode quality = metadata.path("data_quality");
        if (quality.isMissingNode() || quality.isNull() || quality.isEmpty()) {
            quality = metadata.path("mstarpy_metadata").path("data_quality");
        }
        JsonNode flags = quality.path("quality_flags");
        if (!flags.isArray() || flags.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (JsonNode flag : flags) {
            if (flag.isTextual()) {
                result.add(flag.asText());
            }
        }
        return result;
    }
}
