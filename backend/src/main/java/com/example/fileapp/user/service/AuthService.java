package com.example.fileapp.user.service;

import com.example.fileapp.common.ApiException;
import com.example.fileapp.security.JwtProvider;
import com.example.fileapp.user.domain.Role;
import com.example.fileapp.user.domain.User;
import com.example.fileapp.user.dto.AuthDtos.*;
import com.example.fileapp.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse signup(SignupRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "username already exists");
        }
        User user = User.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .email(req.email())
                .role(Role.USER)
                .build();
        userRepository.save(user);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtProvider.parse(req.refreshToken());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not a refresh token");
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "user not found"));
        return issue(user);
    }

    private TokenResponse issue(User user) {
        return new TokenResponse(
                jwtProvider.createAccessToken(user),
                jwtProvider.createRefreshToken(user),
                user.getUsername(),
                user.getRole().name()
        );
    }
}
