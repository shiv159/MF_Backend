package com.mutualfunds.api.mutual_fund.shared.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class FundMetadataExtractor {

    public JsonNode findNavHistory(JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata.has("nav_history")) {
            return metadata.get("nav_history");
        }
        if (metadata.has("mstarpy_metadata") && metadata.get("mstarpy_metadata").has("nav_history")) {
            return metadata.get("mstarpy_metadata").get("nav_history");
        }
        return null;
    }

    public Double extractMetric(JsonNode metadata, String... keys) {
        if (metadata == null) {
            return null;
        }

        for (String key : keys) {
            if (metadata.has(key) && !metadata.get(key).isNull()) {
                return metadata.get(key).asDouble();
            }

            if (metadata.has("mstarpy_metadata")) {
                JsonNode mstar = metadata.get("mstarpy_metadata");
                if (mstar.has(key) && !mstar.get(key).isNull()) {
                    return mstar.get(key).asDouble();
                }
            }

            JsonNode riskVol = metadata.at("/risk_volatility/fund_risk_volatility/for3Year");
            if (!riskVol.isMissingNode() && riskVol.has(key) && !riskVol.get(key).isNull()) {
                return riskVol.get(key).asDouble();
            }
        }

        return null;
    }

    public JsonNode resolveStatsNode(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        if (metadata.has("mstarpy_metadata") && metadata.get("mstarpy_metadata").isObject()) {
            return metadata.get("mstarpy_metadata");
        }
        return metadata;
    }
}
