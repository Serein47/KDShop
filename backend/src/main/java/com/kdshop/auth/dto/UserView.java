package com.kdshop.auth.dto;

import java.util.List;

public record UserView(
        Long id,
        String nickname,
        String email,
        String phone,
        boolean emailVerified,
        List<String> roles
) {
}
