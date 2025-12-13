package com.mutualfunds.api.mutual_fund.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Excel parsing components
 */
@Configuration
public class ExcelParsingConfig {
    
    /**
     * Create Tika bean for Excel content extraction
     * 
     * @return Tika instance for parsing Excel files
     */
    @Bean
    public Tika tika() {
        return new Tika();
    }
}
