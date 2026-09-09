package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.entity.RefreshToken;
import org.praskanth.entity.User;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.RefreshTokenRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepo refreshTokenRepo;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public Duration getRefreshTokenDuration() {
        return Duration.ofDays(refreshExpirationDays);
    }

    @Transactional
    public RefreshToken createOrUpdateRefreshToken(User user) {
        RefreshToken refreshToken = refreshTokenRepo.findByUser(user)
                .orElseGet(() -> RefreshToken.builder()
                        .user(user)
                        .build());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plus(Duration.ofDays(refreshExpirationDays)));
        refreshToken.setRevoked(false);
        return refreshTokenRepo.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken verifyExpiration(String token){
        RefreshToken refreshToken = refreshTokenRepo.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isRevoked()){
            throw new UnauthorizedException("Refresh token was revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())){
            throw new UnauthorizedException("Refresh token is expired. Please login again");
        }
        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token){
        refreshTokenRepo.findByToken(token).ifPresent(rt ->{
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
        });
    }
}
