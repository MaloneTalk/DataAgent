package io.github.malonetalk.agent;

import lombok.Builder;

@Builder
public record ToolCallContext(String sessionId) {
}
