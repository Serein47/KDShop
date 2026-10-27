package com.kdshop.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 把异常统一转换成 ApiResponse，保证前端只处理一种结构。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        log.info("business error code={} message={} traceId={}", code.code(), ex.getMessage(), TraceId.current());
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(code, ex.getMessage(), ex.data()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.status())
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, null, Map.of("fields", fields)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.status())
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, ex.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.BODY_MALFORMED.status())
                .body(ApiResponse.fail(ErrorCode.BODY_MALFORMED, null, null));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(ErrorCode.ENDPOINT_NOT_FOUND.status())
                .body(ApiResponse.fail(ErrorCode.ENDPOINT_NOT_FOUND, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("unexpected error traceId={}", TraceId.current(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, null, null));
    }
}
