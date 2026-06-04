package io.github.malonetalk.dto;

import jakarta.validation.constraints.NotBlank;

public record DomainCreateRequest(
        @NotBlank(message = "领域名称不能为空") String name, String description) {}
