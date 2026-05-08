package io.github.malonetalk.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "sessionId 不能为空") String sessionId,
        @NotBlank(message = "message 不能为空") String message) {}
