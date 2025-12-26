package com.mutualfunds.api.mutual_fund.service.manual.contract;

import com.mutualfunds.api.mutual_fund.dto.manual.ManualSelectionRequest;
import com.mutualfunds.api.mutual_fund.dto.manual.ManualSelectionResponse;

public interface IManualSelectionService {

    ManualSelectionResponse replaceHoldingsWithManualSelection(ManualSelectionRequest request);
}
