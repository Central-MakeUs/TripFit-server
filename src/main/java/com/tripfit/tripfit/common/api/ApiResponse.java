package com.tripfit.tripfit.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message, String code) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data, null, null);
	}

	public static <T> ApiResponse<T> of(T data, String code, String message) {
		return new ApiResponse<>(data, message, code);
	}
}
