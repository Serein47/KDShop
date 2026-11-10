package com.kdshop.coupon.dto;

import java.util.List;

public record GrantRequest(List<String> emails, boolean allUsers) {
}
