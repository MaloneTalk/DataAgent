CREATE TABLE IF NOT EXISTS `datasource` (
    `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(255) DEFAULT NULL COMMENT '数据源名称',
    `type` VARCHAR(50) DEFAULT NULL COMMENT '数据源类型',
    `host` VARCHAR(255) DEFAULT NULL COMMENT '主机地址',
    `port` INT(11) DEFAULT NULL COMMENT '端口号',
    `database_name` VARCHAR(255) DEFAULT NULL COMMENT '数据库名称',
    `username` VARCHAR(255) DEFAULT NULL COMMENT '用户名',
    `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
    `connection_url` VARCHAR(500) DEFAULT NULL COMMENT '连接URL',
    `status` VARCHAR(20) DEFAULT NULL COMMENT '状态',
    `test_status` VARCHAR(20) DEFAULT NULL COMMENT '测试状态',
    `description` TEXT DEFAULT NULL COMMENT '描述',
    `creator_id` BIGINT(20) DEFAULT NULL COMMENT '创建者ID',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

CREATE TABLE IF NOT EXISTS `table_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_name` VARCHAR(255) NOT NULL COMMENT '表名',
    `table_description` VARCHAR(500) DEFAULT NULL COMMENT '表描述',
    `domain` VARCHAR(255) DEFAULT NULL COMMENT '域',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否激活',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_table` (`datasource_id`, `table_name`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_is_active` (`is_active`),
    KEY `idx_is_visible` (`is_visible`),
    KEY `idx_datasource_active` (`datasource_id`, `is_active`),
    KEY `idx_datasource_active_visible` (`datasource_id`, `is_active`, `is_visible`),
    KEY `idx_datasource_visible_table` (`datasource_id`, `is_active`, `is_visible`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表信息表';

CREATE TABLE IF NOT EXISTS `column_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `table_name` VARCHAR(255) NOT NULL COMMENT '表名',
    `column_name` VARCHAR(255) NOT NULL COMMENT '列名',
    `column_description` VARCHAR(500) DEFAULT NULL COMMENT '列语义描述',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否激活',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_table_column` (`datasource_id`, `table_name`, `column_name`),
    KEY `idx_datasource_table_visible` (`datasource_id`, `table_name`, `is_active`, `is_visible`),
    KEY `idx_datasource_table_visible_column`
            (`datasource_id`, `table_name`, `is_active`, `is_visible`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列语义信息表';

CREATE TABLE IF NOT EXISTS `logical_table_relation` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `source_table_name` VARCHAR(255) NOT NULL COMMENT '源表名',
    `source_column_names_json` TEXT NOT NULL COMMENT '源列名JSON',
    `source_column_signature` VARCHAR(500) NOT NULL COMMENT '源列签名',
    `target_table_name` VARCHAR(255) NOT NULL COMMENT '目标表名',
    `target_column_names_json` TEXT NOT NULL COMMENT '目标列名JSON',
    `target_column_signature` VARCHAR(500) NOT NULL COMMENT '目标列签名',
    `relation_type` VARCHAR(64) NOT NULL COMMENT '关系类型',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '关系描述',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_relation_source_signature`
            (`datasource_id`, `source_table_name`, `source_column_signature`),
    KEY `idx_relation_source_table` (`datasource_id`, `source_table_name`),
    KEY `idx_relation_source_enabled` (`datasource_id`, `source_table_name`, `is_enabled`),
    KEY `idx_relation_source_enabled_id` (`datasource_id`, `source_table_name`, `is_enabled`, `id`),
    KEY `idx_relation_source_target_id`
            (`datasource_id`, `source_table_name`, `target_table_name`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑表关系表（查询依赖 ci collation 做大小写不敏感匹配）';

CREATE TABLE IF NOT EXISTS `agentscope_skills` (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    skill_content LONGTEXT NOT NULL,
    source VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agentscope_skill_resources` (
    id BIGINT NOT NULL,
    resource_path VARCHAR(500) NOT NULL,
    resource_content LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id, resource_path),
    FOREIGN KEY (id) REFERENCES agentscope_skills(id) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
