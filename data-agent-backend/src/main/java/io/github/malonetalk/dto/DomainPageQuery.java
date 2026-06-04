package io.github.malonetalk.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record DomainPageQuery(
        @Min(1) Integer page,
        @Min(1) Integer pageSize,
        String keyword,
        @Pattern(regexp = "^(?i)(asc|desc)$", message = "sortOrder must be asc or desc.")
                String sortOrder) {}
