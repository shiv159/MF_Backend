package com.mutualfunds.api.mutual_fund.service.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WealthProjectionServiceTest {

    private ObjectMapper objectMapper;
    private WealthProjectionService wealthProjectionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        wealthProjectionService = new WealthProjectionService(objectMapper);
    }

    @Test
    void extractFundStatsSupportsRootLevelMetadataContract() throws Exception {
        ObjectNode rootMetadata = objectMapper.createObjectNode();
        rootMetadata.put("alpha", 5.0);
        rootMetadata.put("beta", 1.2);
        rootMetadata.put("stdev", 18.0);

        Fund fund = Fund.builder()
                .fundId(UUID.randomUUID())
                .fundName("Root Metadata Fund")
                .isin("ROOTMETA12345")
                .fundMetadataJson(rootMetadata)
                .build();

        Object stats = extractStats(fund);
        double meanReturn = readStat(stats, "meanReturn");
        double stdDev = readStat(stats, "stdDev");

        assertThat(meanReturn).isCloseTo(0.184, within(0.0001));
        assertThat(stdDev).isCloseTo(0.18, within(0.0001));
    }

    @Test
    void extractFundStatsSupportsNestedMstarMetadataContract() throws Exception {
        ObjectNode nested = objectMapper.createObjectNode();
        nested.put("alpha", 2.5);
        nested.put("beta", 0.9);
        nested.put("stdev", 14.0);

        ObjectNode wrappedMetadata = objectMapper.createObjectNode();
        wrappedMetadata.set("mstarpy_metadata", nested);

        Fund fund = Fund.builder()
                .fundId(UUID.randomUUID())
                .fundName("Nested Metadata Fund")
                .isin("NESTMETA1234")
                .fundMetadataJson(wrappedMetadata)
                .build();

        Object stats = extractStats(fund);
        double meanReturn = readStat(stats, "meanReturn");
        double stdDev = readStat(stats, "stdDev");

        assertThat(meanReturn).isCloseTo(0.138, within(0.0001));
        assertThat(stdDev).isCloseTo(0.14, within(0.0001));
    }

    private Object extractStats(Fund fund) throws Exception {
        Method method = WealthProjectionService.class.getDeclaredMethod("extractFundStats", Fund.class);
        method.setAccessible(true);
        return method.invoke(wealthProjectionService, fund);
    }

    private double readStat(Object stats, String accessorName) throws Exception {
        Method accessor = stats.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return (double) accessor.invoke(stats);
    }
}
