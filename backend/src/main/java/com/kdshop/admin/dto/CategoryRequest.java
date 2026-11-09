package com.kdshop.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        Long parentId,
        @NotBlank(message = "类目名称不能为空") @Size(max = 32) String name,
        Integer sort,
        Boolean featured,
        String status
) {
}
