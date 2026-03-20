package com.mutualfunds.api.mutual_fund.features.funds.api;

import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;

import java.util.Map;

public interface FundUpsertService {

    FundUpsertResult upsertFromEnriched(Map<String, Object> holding, boolean requireNav);

    record FundUpsertResult(Fund fund, boolean created) {
    }
}
