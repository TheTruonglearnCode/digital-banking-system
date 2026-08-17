package com.digitalbank.auth.service;


import com.digitalbank.auth.dto.request.LoginRequest;
import com.digitalbank.auth.dto.request.RegisterRequest;
import com.digitalbank.auth.dto.response.LoginResponse;
import com.digitalbank.auth.dto.response.RefreshTokenResponse;
import com.digitalbank.auth.dto.response.UserResponse;
import com.digitalbank.auth.entity.RefreshToken;
import com.digitalbank.auth.entity.Role;
import com.digitalbank.auth.entity.User;
import com.digitalbank.auth.enums.UserStatus;
import com.digitalbank.auth.exception.*;
import com.digitalbank.auth.mapper.UserMapper;
import com.digitalbank.auth.repository.RefreshTokenRepository;
import com.digitalbank.auth.repository.RoleRepository;
import com.digitalbank.auth.repository.UserRepository;
import com.digitalbank.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 15;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneNumberAlreadyExistsException(request.getPhoneNumber());
        }

        Role role = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RoleNotFoundException("CUSTOMER"));

        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING)
                .role(role)
                .build();

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Account is locked. Try again after " + user.getLockedUntil());
        }

        boolean needSave = false;

        if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            needSave = true;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is not active. Current status: " + user.getStatus());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_TIME_MINUTES));
            }
            userRepository.save(user);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (needSave || user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration() / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .build();
    }

    @Transactional
    public RefreshTokenResponse refreshToken(String token) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException(
                                "Invalid refresh token"
                        )
                );

        if (refreshToken.isRevoked()) {
            throw new InvalidRefreshTokenException(
                    "Refresh token has been revoked"
            );
        }

        if (refreshToken.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new InvalidRefreshTokenException(
                    "Refresh token has expired"
            );
        }

        User user = refreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        // Revoke token cũ
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Tạo token mới
        String newAccessToken =
                jwtService.generateAccessToken(user);

        String newRefreshToken =
                jwtService.generateRefreshToken(user);

        // Lưu refresh token mới
        RefreshToken newRefreshTokenEntity =
                RefreshToken.builder()
                        .user(user)
                        .token(newRefreshToken)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusSeconds(
                                                jwtService
                                                        .getRefreshTokenExpiration() / 1000
                                        )
                        )
                        .revoked(false)
                        .build();

        refreshTokenRepository.save(newRefreshTokenEntity);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .build();
    }

}
