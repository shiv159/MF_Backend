package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private UUID uploadId;
    private String status;
}