package com.mtole.taskmanager.auth;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<Long, RefreshToken> refreshTokens = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final AtomicLong familyIdCounter = new AtomicLong();

    public InMemoryRefreshTokenRepository() {
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setId(counter.incrementAndGet());
        }
        refreshTokens.put(refreshToken.getId(), refreshToken);
        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokens.values().stream()
                .filter(refreshToken -> token.equals(refreshToken.getToken()))
                .findFirst();
    }

    @Override
    public void revokeFamily(Long familyId) {
        List<RefreshToken> toRemove = findAllByFamilyId(familyId);
        for (RefreshToken refreshToken : toRemove) {
            refreshToken.setRevoked(true);
        }
    }

    @Override
    public Long nextFamilyId() {
        return familyIdCounter.incrementAndGet();
    }

    private List<RefreshToken> findAllByFamilyId(Long familyId) {
        return refreshTokens.values().stream()
                .filter(t -> familyId.equals(t.getFamilyId()))
                .toList();
    }
}
