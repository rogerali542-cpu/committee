package com.ywh.config;

import com.ywh.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.forbidden("权限不足：" + e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<?>> handleRuntime(RuntimeException e) {
        log.error("Runtime error", e);
        return ResponseEntity.badRequest().body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部错误"));
    }
}
