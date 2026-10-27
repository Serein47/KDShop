package com.kdshop.auth.dto;

public record RegisterResult(Long userId, String email, boolean verifyEmailSent) {
}
