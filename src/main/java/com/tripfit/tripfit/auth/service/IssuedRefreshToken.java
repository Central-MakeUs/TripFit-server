package com.tripfit.tripfit.auth.service;

import java.util.UUID;

record IssuedRefreshToken(
    String token,
    UUID userId,
    String familyId
) {
}
