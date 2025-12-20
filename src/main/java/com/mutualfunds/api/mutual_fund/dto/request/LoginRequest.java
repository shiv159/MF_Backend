package com.mutualfunds.api.mutual_fund.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Password is required")
    private String password;
}