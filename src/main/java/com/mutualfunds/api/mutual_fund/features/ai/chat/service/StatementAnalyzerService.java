package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StatementAnalyzerService {

    private static final String EXTRACTION_PROMPT = """
            You are analyzing an Indian mutual fund statement (CAS - Consolidated Account Statement).
            Extract all mutual fund holdings from this document.

            For each fund, extract:
            - fundName: full scheme name
            - units: number of units held
            - nav: latest NAV if shown
            - currentValue: market value if shown
            - investmentAmount: cost of acquisition if shown
            - folio: folio number if shown

            Respond with ONLY valid JSON:
            {"holdings":[{"fundName":"...","units":0,"nav":0,"currentValue":0,"investmentAmount":0,"folio":"..."}]}

            If you cannot extract any holdings, respond: {"holdings":[],"error":"reason"}
            """;

    private final ChatClient synthesisChatClient;
    private final FundQueryService fundQueryService;
    private final PortfolioReadService portfolioReadService;
    private final ObjectMapper objectMapper;

    public StatementAnalyzerService(@Qualifier("synthesisChatClient") ChatClient synthesisChatClient,
                                     FundQueryService fundQueryService,
                                     PortfolioReadService portfolioReadService,
                                     ObjectMapper objectMapper) {
        this.synthesisChatClient = synthesisChatClient;
        this.fundQueryService = fundQueryService;
        this.portfolioReadService = portfolioReadService;
        this.objectMapper = objectMapper;
    }

    public ObjectNode analyzeStatement(UUID userId, MultipartFile file) {
        ObjectNode result = objectMapper.createObjectNode();

        try {
            String contentType = file.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            MimeType mimeType;
            if (contentType.contains("pdf")) {
                mimeType = MimeType.valueOf("application/pdf");
            } else if (contentType.contains("png")) {
                mimeType = MimeTypeUtils.IMAGE_PNG;
            } else if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                mimeType = MimeTypeUtils.IMAGE_JPEG;
            } else {
                result.put("error", "Unsupported file type: " + contentType + ". Please upload PDF, PNG, or JPG.");
                return result;
            }

            Media media = Media.builder()
                    .mimeType(mimeType)
                    .data(new ByteArrayResource(file.getBytes()))
                    .build();

            String response = synthesisChatClient.prompt()
                    .user(u -> u.text(EXTRACTION_PROMPT).media(media))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                result.put("error", "Could not extract data from the uploaded document.");
                return result;
            }

            String normalized = response.strip();
            if (normalized.startsWith("```")) {
                normalized = normalized.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode parsed = objectMapper.readTree(normalized);
            if (parsed.has("error")) {
                result.put("error", parsed.path("error").asText());
                return result;
            }

            ArrayNode extractedHoldings = (ArrayNode) parsed.path("holdings");
            result.put("extractedCount", extractedHoldings.size());

            List<UserHolding> existingHoldings = portfolioReadService.findHoldingsWithFund(userId);
            Set<String> existingFundNames = existingHoldings.stream()
                    .filter(h -> h.getFund() != null && h.getFund().getFundName() != null)
                    .map(h -> h.getFund().getFundName().toLowerCase())
                    .collect(Collectors.toSet());

            ArrayNode matched = objectMapper.createArrayNode();
            ArrayNode newFunds = objectMapper.createArrayNode();
            ArrayNode unmatched = objectMapper.createArrayNode();

            for (JsonNode holding : extractedHoldings) {
                String name = holding.path("fundName").asText("");
                if (name.isBlank()) continue;

                List<Fund> dbMatches = fundQueryService.findByFundNameContainingIgnoreCase(name);
                if (!dbMatches.isEmpty()) {
                    Fund dbFund = dbMatches.getFirst();
                    ObjectNode entry = objectMapper.createObjectNode();
                    entry.put("extractedName", name);
                    entry.put("matchedFundName", dbFund.getFundName());
                    entry.put("fundId", dbFund.getFundId().toString());
                    entry.put("category", dbFund.getFundCategory());
                    if (holding.has("units")) entry.put("units", holding.path("units").asDouble());
                    if (holding.has("currentValue")) entry.put("currentValue", holding.path("currentValue").asDouble());
                    if (holding.has("investmentAmount")) entry.put("investmentAmount", holding.path("investmentAmount").asDouble());

                    boolean alreadyHeld = existingFundNames.stream()
                            .anyMatch(existing -> existing.contains(dbFund.getFundName().toLowerCase())
                                    || dbFund.getFundName().toLowerCase().contains(existing));

                    entry.put("alreadyInPortfolio", alreadyHeld);
                    if (alreadyHeld) {
                        matched.add(entry);
                    } else {
                        newFunds.add(entry);
                    }
                } else {
                    ObjectNode entry = objectMapper.createObjectNode();
                    entry.put("extractedName", name);
                    entry.put("status", "NOT_FOUND_IN_DATABASE");
                    if (holding.has("units")) entry.put("units", holding.path("units").asDouble());
                    if (holding.has("currentValue")) entry.put("currentValue", holding.path("currentValue").asDouble());
                    unmatched.add(entry);
                }
            }

            result.set("matchedToPortfolio", matched);
            result.set("newFunds", newFunds);
            result.set("unmatched", unmatched);
            result.put("matchedCount", matched.size());
            result.put("newFundCount", newFunds.size());
            result.put("unmatchedCount", unmatched.size());

        } catch (Exception e) {
            log.error("Statement analysis failed: {}", e.getMessage(), e);
            result.put("error", "Failed to analyze statement: " + e.getMessage());
        }

        return result;
    }
}
