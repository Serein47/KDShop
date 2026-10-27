package com.kdshop.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsToCodeAndStatus() {
        BusinessException ex = new BusinessException(
                ErrorCode.STOCK_INSUFFICIENT, "库存不足，当前仅剩 3 件", Map.of("available", 3));

        ResponseEntity<ApiResponse<Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40002);
        assertThat(response.getBody().message()).isEqualTo("库存不足，当前仅剩 3 件");
        assertThat(response.getBody().data()).isEqualTo(Map.of("available", 3));
    }

    @Test
    void validationErrorCollectsFieldMessages() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "邮箱格式不正确"));
        binding.addError(new FieldError("request", "password", "密码至少 8 位"));

        ResponseEntity<ApiResponse<Object>> response =
                handler.handleValidation(new MethodArgumentNotValidException(null, binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(10001);
        assertThat(response.getBody().data()).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("fields");
    }

    @Test
    void unexpectedErrorHidesDetails() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUnexpected(new IllegalStateException("数据库密码是 xxx"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(50001);
        assertThat(response.getBody().message()).doesNotContain("数据库密码");
    }
}
