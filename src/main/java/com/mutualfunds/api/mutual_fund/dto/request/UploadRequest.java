package com.mutualfunds.api.mutual_fund.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UploadRequest {
    @NotNull
    private UUID userId;

    @NotNull
    private String fileName;

    @NotNull
    private String fileContent; // base64

    @NotNull
    private String fileType;

    private String password; // optional
}