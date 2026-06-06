package com.mtole.taskmanager.auth;

import java.time.LocalDateTime;

public class RefreshToken {
    private Long id;
    private String token;
    private Long userId;
    private Long familyId;
    private LocalDateTime expiresAt; // lo he cambiado como me sugerías
    private boolean revoked;

    public RefreshToken(String token, Long userId, Long familyId, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
