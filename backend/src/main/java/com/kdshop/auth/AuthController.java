package com.kdshop.auth;

import com.kdshop.auth.dto.LoginRequest;
import com.kdshop.auth.dto.RegisterRequest;
import com.kdshop.auth.dto.RegisterResult;
import com.kdshop.auth.dto.EmailRequest;
import com.kdshop.auth.dto.ResetPasswordRequest;
import com.kdshop.auth.dto.TokenRequest;
import com.kdshop.auth.dto.UserView;
import com.kdshop.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;

    public AuthController(AuthService authService, VerificationService verificationService) {
        this.authService = authService;
        this.verificationService = verificationService;
    }

    /** 前端启动时取一次 CSRF token，同时下发 XSRF-TOKEN Cookie。 */
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        return ApiResponse.ok(authService.login(request, httpRequest, httpResponse));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/verify-email")
    public ApiResponse<Map<String, Boolean>> verifyEmail(@Valid @RequestBody TokenRequest request) {
        verificationService.verifyEmail(request.token());
        return ApiResponse.ok(Map.of("verified", true));
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Map<String, Boolean>> resendVerification(@Valid @RequestBody EmailRequest request) {
        verificationService.resendVerification(request.email());
        return ApiResponse.ok(Map.of("sent", true));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, Boolean>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        verificationService.forgotPassword(request.email());
        return ApiResponse.ok(Map.of("sent", true));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Boolean>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                          HttpServletRequest httpRequest) {
        verificationService.resetPassword(request.token(), request.newPassword(), httpRequest);
        return ApiResponse.ok(Map.of("reset", true));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(authService.currentUser());
    }
}
