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

    /** 接口不存在 / 方法不匹配：多半是后端还跑着旧代码。别让它掉进 Exception 兜底变成
     *  「服务器内部错误」（0722 撤回投票、改参会状态两次都被这个误导过）。 */
    @ExceptionHandler({org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Result<?>> handleNoHandler(Exception e) {
        log.warn("接口不存在或方法不匹配: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail("接口不存在：后端可能还在运行旧版本，请重启后端服务后再试"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<?>> handleRuntime(RuntimeException e) {
        log.error("Runtime error", e);
        return ResponseEntity.badRequest().body(Result.fail(e.getMessage()));
    }

    /** 缺参数（如 ?value= 没传）：给出参数名，别掉进下面的兜底变成没头绪的 500。 */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(Result.fail("缺少请求参数：" + e.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("Unexpected error", e);
        // 测试期带上异常类名，前端弹的错误就能直接定位问题类型（纯"服务器内部错误"没法排查）
        String brief = e.getMessage() != null && e.getMessage().length() <= 60 ? "：" + e.getMessage() : "";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部错误（" + e.getClass().getSimpleName() + brief + "）"));
    }
}
