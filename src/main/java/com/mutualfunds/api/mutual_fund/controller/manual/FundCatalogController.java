package com.mutualfunds.api.mutual_fund.controller.manual;

import com.mutualfunds.api.mutual_fund.dto.manual.FundPickerItemResponse;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fund catalog endpoints to support manual selection UI.
 */
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
@Slf4j
public class FundCatalogController {

    private final FundRepository fundRepository;

    @GetMapping
    public ResponseEntity<List<FundPickerItemResponse>> searchFunds(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        int safeLimit = Math.max(1, Math.min(limit, 100));

        List<Fund> funds;
        if (query == null || query.trim().isEmpty()) {
            funds = fundRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "fundName")))
                    .getContent();
        } else {
            funds = fundRepository.findByFundNameContainingIgnoreCase(query.trim());
            if (funds.size() > safeLimit) {
                funds = funds.subList(0, safeLimit);
            }
        }

        List<FundPickerItemResponse> response = funds.stream()
                .map(this::toPickerItem)
                .toList();

        return ResponseEntity.ok(response);
    }

    private FundPickerItemResponse toPickerItem(Fund fund) {
        return FundPickerItemResponse.builder()
                .fundId(fund.getFundId())
                .fundName(fund.getFundName())
                .isin(fund.getIsin())
                .amcName(fund.getAmcName())
                .fundCategory(fund.getFundCategory())
                .directPlan(fund.getDirectPlan())
                .currentNav(fund.getCurrentNav())
                .navAsOf(fund.getNavAsOf())
                .build();
    }
}
