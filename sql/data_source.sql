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
    `physical_table_description` VARCHAR(500) DEFAULT NULL COMMENT '物理表原始描述',
    `table_description` VARCHAR(500) DEFAULT NULL COMMENT '表描述',
    `domain` VARCHAR(255) DEFAULT NULL COMMENT '领域',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `physical_status` TINYINT(1) DEFAULT 1 COMMENT '物理表是否存在',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_table` (`datasource_id`, `table_name`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_is_visible` (`is_visible`),
    KEY `idx_datasource_visible` (`datasource_id`, `is_visible`),
    KEY `idx_datasource_visible_table` (`datasource_id`, `is_visible`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表信息表';

CREATE TABLE IF NOT EXISTS `column_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `table_name` VARCHAR(255) NOT NULL COMMENT '表名',
    `column_name` VARCHAR(255) NOT NULL COMMENT '列名',
    `physical_column_description` VARCHAR(500) DEFAULT NULL COMMENT '物理列原始描述',
    `type_name` VARCHAR(255) DEFAULT NULL COMMENT '物理列类型',
    `primary_key` TINYINT(1) DEFAULT NULL COMMENT '是否物理主键',
    `column_description` VARCHAR(500) DEFAULT NULL COMMENT '列描述',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `physical_status` TINYINT(1) DEFAULT 1 COMMENT '物理列是否存在',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_table_column` (`datasource_id`, `table_name`, `column_name`),
    KEY `idx_datasource_table_visible` (`datasource_id`, `table_name`, `is_visible`),
    KEY `idx_datasource_table_visible_column`
        (`datasource_id`, `table_name`, `is_visible`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列信息表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑表关系表';

CREATE TABLE IF NOT EXISTS `domain_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(255) NOT NULL COMMENT '领域名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '领域描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_domain_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据领域表';

INSERT INTO `domain_info` (`name`, `description`, `create_time`, `update_time`)
SELECT 'default', '默认领域', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `domain_info` WHERE `name` = 'default');

CREATE TABLE IF NOT EXISTS `report` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content` LONGTEXT NOT NULL COMMENT '报告正文',
    `title` VARCHAR(500) NOT NULL COMMENT '报告标题',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
    `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '是否删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';

CREATE TABLE IF NOT EXISTS `scheduled_agent_task` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `name` VARCHAR(255) NOT NULL COMMENT 'Task name',
    `prompt` TEXT NOT NULL COMMENT 'Prompt sent to the agent on each run',
    `schedule_type` VARCHAR(32) NOT NULL COMMENT 'DAILY, INTERVAL, or CRON',
    `schedule_expr` VARCHAR(128) NOT NULL COMMENT 'HH:mm[:ss], ISO-8601 duration, or cron',
    `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Task timezone',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether dispatch can pick up the task',
    `running` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether a run currently owns the task',
    `lock_until` DATETIME DEFAULT NULL COMMENT 'Run lock expiration',
    `lock_owner` VARCHAR(64) DEFAULT NULL COMMENT 'Run lock owner token',
    `current_run_id` INT DEFAULT NULL COMMENT 'Current run id while running',
    `session_mode` VARCHAR(32) NOT NULL DEFAULT 'NEW_EACH_RUN' COMMENT 'NEW_EACH_RUN or FIXED_SESSION',
    `session_id` VARCHAR(255) DEFAULT NULL COMMENT 'Fixed session id when session_mode is FIXED_SESSION',
    `next_run_at` DATETIME NOT NULL COMMENT 'Next due time',
    `last_run_at` DATETIME DEFAULT NULL COMMENT 'Last dispatch time',
    `last_status` VARCHAR(32) DEFAULT NULL COMMENT 'Last run status',
    `last_error` TEXT DEFAULT NULL COMMENT 'Last run error',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    KEY `idx_due_task` (`enabled`, `next_run_at`),
    KEY `idx_lock` (`running`, `lock_until`),
    KEY `idx_lock_owner` (`lock_owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Scheduled agent task';

CREATE TABLE IF NOT EXISTS `scheduled_agent_task_run` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `task_id` INT NOT NULL COMMENT 'Task id',
    `session_id` VARCHAR(255) NOT NULL COMMENT 'Agent session id',
    `status` VARCHAR(32) NOT NULL COMMENT 'RUNNING, SUCCESS, FAILED, or NEEDS_USER',
    `report_id` INT DEFAULT NULL COMMENT 'Generated report id',
    `output_summary` TEXT DEFAULT NULL COMMENT 'Final agent text or summary',
    `error_message` TEXT DEFAULT NULL COMMENT 'Failure reason',
    `started_at` DATETIME NOT NULL COMMENT 'Start time',
    `finished_at` DATETIME DEFAULT NULL COMMENT 'Finish time',
    PRIMARY KEY (`id`),
    KEY `idx_task_started_at` (`task_id`, `started_at`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Scheduled agent task run';
