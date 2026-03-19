package com.mutualfunds.api.mutual_fund.features.portfolio.manual.api;

import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionResponse;

public interface IManualSelectionService {

    ManualSelectionResponse replaceHoldingsWithManualSelection(ManualSelectionRequest request);
}
