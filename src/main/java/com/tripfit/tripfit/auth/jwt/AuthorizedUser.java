package com.tripfit.tripfit.auth.jwt;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Controller 파라미터에 JWT principal(UUID userId) 주입 — AuthorizedUserArgumentResolver
// @Parameter(hidden = true): springdoc이 커스텀 어노테이션을 몰라 평범한 UUID 쿼리 파라미터로 노출하던 것을 숨김
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface AuthorizedUser {
}
