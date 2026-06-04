package io.github.malonetalk.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DomainInfo {

    private Integer id;
    private String name;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
