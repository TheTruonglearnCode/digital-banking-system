package com.digitalbank.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class LogoutRequest {
    @NotBlank(message = "Refresh Token is required")
    private String refreshToken;    
}
