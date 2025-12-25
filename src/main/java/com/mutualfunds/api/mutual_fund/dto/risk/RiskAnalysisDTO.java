package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskAnalysisDTO {
    private Integer score;
    private String level;
    private String rationale;
}
