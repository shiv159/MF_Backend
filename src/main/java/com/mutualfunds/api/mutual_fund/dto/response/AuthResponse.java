package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private String status;
    private String accessToken;
    private UUID userId;
    private String email;
    private LocalDateTime createdAt;
}