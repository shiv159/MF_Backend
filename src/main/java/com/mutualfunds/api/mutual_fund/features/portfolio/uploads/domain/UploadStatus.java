package com.mutualfunds.api.mutual_fund.features.portfolio.uploads.domain;

public enum UploadStatus {
    parsing,      // File being parsed in Spring Boot
    enriching,    // Holdings sent to Python ETL for enrichment
    completed,    // Enrichment successful, user_holdings inserted
    failed        // Either parsing or enrichment failed
}