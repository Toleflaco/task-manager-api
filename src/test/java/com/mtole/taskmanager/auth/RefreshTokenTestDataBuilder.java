package com.mtole.taskmanager.auth;

import com.mtole.taskmanager.users.User;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class RefreshTokenTestDataBuilder {

    private Long id;
    private String token = "token_refresh";
    private User user = null;
    private UUID familyId  = UUID.randomUUID();
    private OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
    private boolean revoked = false;

    public static RefreshTokenTestDataBuilder aRefreshToken() {
        return new RefreshTokenTestDataBuilder();
    }

    public RefreshTokenTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }
    public RefreshTokenTestDataBuilder withToken(String token) {
        this.token = token;
        return this;
    }
    public RefreshTokenTestDataBuilder withUser(User user) {
        this.user = user;
        return this;
    }
    public RefreshTokenTestDataBuilder withFamilyId(UUID familyId) {
        this.familyId = familyId;
        return this;
    }
    public RefreshTokenTestDataBuilder withExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
    public RefreshTokenTestDataBuilder withRevoked(boolean revoked) {
        this.revoked = revoked;
        return this;
    }

    public RefreshToken build() {
        RefreshToken refreshToken = new RefreshToken();
        if (id != null)
            refreshToken.setId(id);
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRevoked(revoked);
        return refreshToken;
    }
}
