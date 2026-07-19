package com.tripfit.tripfit.auth.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Controller 파라미터에 JWT principal(UUID userId) 주입 — AuthorizedUserArgumentResolver
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthorizedUser {
}
