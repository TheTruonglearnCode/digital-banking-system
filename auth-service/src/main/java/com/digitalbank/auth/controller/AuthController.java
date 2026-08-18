package com.digitalbank.auth.controller;


import com.digitalbank.auth.config.RateLimitService;
import com.digitalbank.auth.dto.request.LoginRequest;
import com.digitalbank.auth.dto.request.LogoutRequest;
import com.digitalbank.auth.dto.request.RefreshTokenRequest;
import com.digitalbank.auth.dto.request.RegisterRequest;
import com.digitalbank.auth.dto.response.LoginResponse;
import com.digitalbank.auth.dto.response.RefreshTokenResponse;
import com.digitalbank.auth.dto.response.UserResponse;
import com.digitalbank.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        rateLimitService.checkLoginRateLimit("login:" + clientIp);
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(
                authService.refreshToken(
                        request.getRefreshToken()
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request){
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
