package com.mutualfunds.api.mutual_fund.dto.manual;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionResult {

    private UUID inputFundId;
    private String inputFundName;

    private String status; // RESOLVED_FROM_DB | CREATED_FROM_ETL | ERROR

    private UUID fundId;
    private String fundName;
    private String isin;

    private String message;
}
