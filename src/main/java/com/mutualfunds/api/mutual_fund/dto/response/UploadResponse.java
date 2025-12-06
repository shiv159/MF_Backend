package com.mutualfunds.api.mutual_fund.dto.response;

import com.mutualfunds.api.mutual_fund.enums.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private UUID uploadId;
    private UploadStatus status;
}