package io.github.malonetalk.model.bo;

import lombok.Builder;

@Builder
public record UserContextBo(Integer userId, String username, String displayName, Integer roleId, Boolean superAdmin) {
}
