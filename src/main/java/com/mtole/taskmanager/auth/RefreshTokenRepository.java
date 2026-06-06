package com.mtole.taskmanager.auth;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByToken(String token);

    void revokeFamily(Long familyId);
    Long nextFamilyId();


}
