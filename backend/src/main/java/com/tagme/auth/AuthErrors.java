package com.tagme.auth;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthErrors {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<Map<String, Object>> auth(AuthException error) {
        var response = ResponseEntity.status(error.status()).header("Cache-Control", "no-store");
        if (error.retryAfter() > 0) response.header("Retry-After", Integer.toString(error.retryAfter()));
        return response.body(Map.of("code", error.code(), "message", error.getMessage(), "retryAfter", error.retryAfter()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> invalidBody(Exception ignored) {
        return auth(new AuthException(400, "INVALID_INPUT", "입력 형식을 확인해 주세요."));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> infrastructure(DataAccessException ignored) { return auth(AuthException.unavailable()); }
}
