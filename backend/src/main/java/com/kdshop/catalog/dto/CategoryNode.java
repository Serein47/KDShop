package com.kdshop.catalog.dto;

import java.util.List;

public record CategoryNode(Long id, String name, Integer sort, Boolean featured, List<CategoryNode> children) {
}
