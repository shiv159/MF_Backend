package com.mutualfunds.api.mutual_fund.dto.manual;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionHolding {

    private UUID fundId;
    private String fundName;
    private String isin;
    private String amcName;
    private String fundCategory;
    private Boolean directPlan;

    private Double currentNav;
    private java.sql.Date navAsOf;

    private Integer weightPct;

    private JsonNode sectorAllocation;
    private JsonNode topHoldings;
    private JsonNode fundMetadata;
}
