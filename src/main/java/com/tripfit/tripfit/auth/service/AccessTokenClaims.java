package com.tripfit.tripfit.auth.service;

public record AccessTokenClaims(Long userId, String jti) {
}
