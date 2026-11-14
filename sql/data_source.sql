-- MySQL 5.7: initialize a new metadata database; use migration.sql for existing databases.
CREATE TABLE IF NOT EXISTS `datasource` (
    `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(255) DEFAULT NULL COMMENT '数据源名称',
    `type` VARCHAR(50) DEFAULT NULL COMMENT '数据源类型',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'DATABASE' COMMENT '来源：DATABASE/CSV',
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
    `updater_id` BIGINT(20) DEFAULT NULL COMMENT '更新者ID',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

CREATE TABLE IF NOT EXISTS `table_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_name` VARCHAR(255) NOT NULL COMMENT '表名',
    `physical_table_description` VARCHAR(500) DEFAULT NULL COMMENT '物理表原始描述',
    `table_description` VARCHAR(500) DEFAULT NULL COMMENT '表描述',
    `data_granularity` VARCHAR(500) DEFAULT NULL COMMENT '数据粒度（一行代表的业务实体或事件）',
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
    `table_id` INT NOT NULL COMMENT '关联表信息ID',
    `column_name` VARCHAR(255) NOT NULL COMMENT '列名',
    `physical_column_description` VARCHAR(500) DEFAULT NULL COMMENT '物理列原始描述',
    `type_name` VARCHAR(255) DEFAULT NULL COMMENT '物理列类型',
    `primary_key` TINYINT(1) DEFAULT NULL COMMENT '是否物理主键',
    `column_description` VARCHAR(500) DEFAULT NULL COMMENT '列描述',
    `semantic_type` VARCHAR(32) DEFAULT NULL COMMENT '语义类型：IDENTIFIER/DIMENSION/MEASURE/TIME/LOCATION',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `physical_status` TINYINT(1) DEFAULT 1 COMMENT '物理列是否存在',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `index_info` TEXT COMMENT 'physical index info',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_table_column` (`table_id`, `column_name`),
    KEY `idx_datasource_id` (`datasource_id`),
    CONSTRAINT `fk_column_info_table`
        FOREIGN KEY (`table_id`) REFERENCES `table_info` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列信息表';

CREATE TABLE IF NOT EXISTS `logical_table_relation` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `source_table_id` INT NOT NULL COMMENT '源表信息ID',
    `source_column_names_json` TEXT NOT NULL COMMENT '源列名JSON',
    `target_table_id` INT NOT NULL COMMENT '目标表信息ID',
    `target_column_names_json` TEXT NOT NULL COMMENT '目标列名JSON',
    `relation_type` VARCHAR(64) NOT NULL COMMENT '关系类型',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '关系描述',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_relation_source_table` (`source_table_id`),
    KEY `idx_relation_target_table` (`target_table_id`),
    KEY `idx_relation_source_enabled_id` (`datasource_id`, `source_table_id`, `is_enabled`, `id`),
    CONSTRAINT `fk_relation_source_table`
        FOREIGN KEY (`source_table_id`) REFERENCES `table_info` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `fk_relation_target_table`
        FOREIGN KEY (`target_table_id`) REFERENCES `table_info` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
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

CREATE TABLE IF NOT EXISTS `table_export` (
    `id` VARCHAR(36) NOT NULL COMMENT 'export id',
    `title` VARCHAR(255) NOT NULL COMMENT 'export title',
    `row_count` INT NOT NULL DEFAULT 0 COMMENT 'exported row count',
    `session_id` VARCHAR(255) NOT NULL COMMENT 'session id',
    `csv_content` LONGBLOB NOT NULL COMMENT 'csv file content',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`),
    KEY `idx_session_create_time` (`session_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='table export';

CREATE TABLE IF NOT EXISTS  `agentscope_sessions` (
  `session_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `state_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_index` int(11) NOT NULL DEFAULT '0',
  `state_data` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`,`state_key`,`item_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `session_datasource` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`    VARCHAR(255) NOT NULL COMMENT '会话ID',
    `datasource_id` INT          NOT NULL COMMENT '本会话允许/选择的数据源ID',
    `creator_id`    BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updater_id`    BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_session_datasource` (`session_id`, `datasource_id`),
    KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话允许使用的数据源列表';

CREATE TABLE IF NOT EXISTS `answer_provenance` (
    `answer_id` VARCHAR(255) NOT NULL COMMENT '初始用户消息ID',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
    `status` VARCHAR(16) NOT NULL COMMENT '记录状态：COMPLETE/PARTIAL',
    `datasources_json` JSON NOT NULL COMMENT '使用的数据源',
    `sql_executions_json` JSON NOT NULL COMMENT '执行的SQL',
    `metric_references_json` JSON NOT NULL COMMENT '引用的指标口径',
    `python_executions_json` JSON NOT NULL COMMENT '执行的Python代码',
    `tables_json` JSON NOT NULL COMMENT '表和字段血缘',
    `warnings_json` JSON NOT NULL COMMENT '溯源警告',
    PRIMARY KEY (`answer_id`),
    KEY `idx_answer_provenance_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回答数据证据';

CREATE TABLE IF NOT EXISTS `metric_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `metric_key` VARCHAR(255) NOT NULL COMMENT '指标稳定标识(程序化引用用),如 sales',
    `name` VARCHAR(255) NOT NULL COMMENT '指标显示名,如 销售额',
    `aliases` TEXT DEFAULT NULL COMMENT '同义词,逗号分隔,如 GMV,营收,流水',
    `measure_expr` VARCHAR(500) DEFAULT NULL COMMENT '度量表达式,如 SUM(paid_amount)',
    `filters` TEXT DEFAULT NULL COMMENT '过滤条件,如 status=''paid'' AND is_test=0',
    `time_field` VARCHAR(255) DEFAULT NULL COMMENT '时间维度字段,如 settle_time',
    `calculation_type` VARCHAR(32) NOT NULL DEFAULT 'EXPRESSION' COMMENT '计算方式:EXPRESSION/DISTINCT_COUNT/RATIO/SNAPSHOT',
    `value_type` VARCHAR(16) DEFAULT NULL COMMENT '结果类型:NUMBER/MONEY/PERCENT,NULL表示未配置',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '标准结果单位,如元/人/次,百分比比值为1',
    `result_multiplier` DECIMAL(24,12) NOT NULL DEFAULT 1 COMMENT '标准结果=度量表达式原始结果乘以此倍率',
    `currency_code` VARCHAR(3) DEFAULT NULL COMMENT '金额币种代码,如CNY/USD',
    `description` TEXT DEFAULT NULL COMMENT '业务口径说明/规则',
    `creator_id` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater_id` BIGINT DEFAULT NULL COMMENT '修改人ID',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_metric_key` (`datasource_id`, `metric_key`),
    KEY `idx_datasource_name` (`datasource_id`, `name`),
    KEY `idx_datasource_aliases` (`datasource_id`, `aliases`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标口径表';

-- 示例:销售额口径。请按你实际的 datasource_id 调整后再执行(取消注释)。
-- INSERT INTO `metric_info`
--   (`datasource_id`, `metric_key`, `name`, `aliases`,
--    `measure_expr`, `filters`, `time_field`, `calculation_type`, `value_type`,
--    `unit`, `result_multiplier`, `currency_code`, `description`)
-- VALUES
--   (1, 'sales', '销售额', 'GMV,营收,流水,成交额',
--    'SUM(paid_amount)', 'status=''paid'' AND is_test=0', 'settle_time',
--    'EXPRESSION', 'MONEY', '元', 1, 'CNY',
--    '已支付净额,使用结算时间,排除测试账号');

-- 权限管理：角色表 + 表级白名单 + 列级黑名单。
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) NULL COMMENT '角色描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

-- 角色-表白名单：角色可见的表；缺省不可见（新表不会自动泄露）。
CREATE TABLE IF NOT EXISTS `role_table_permission` (
    `id`            INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       INT NOT NULL COMMENT '角色ID',
    `datasource_id` INT NOT NULL COMMENT '数据源ID',
    `table_name`    VARCHAR(128) NOT NULL COMMENT '物理表名（与 table_info.table_name 一致）',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_ds_table` (`role_id`, `datasource_id`, `table_name`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-表 白名单';

-- 角色-隐藏列（黑名单）：记录存在 = 该列对角色不可见。缺省不隐藏。
CREATE TABLE IF NOT EXISTS `role_hidden_column` (
    `id`            INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       INT NOT NULL COMMENT '角色ID',
    `datasource_id` INT NOT NULL COMMENT '数据源ID',
    `table_name`    VARCHAR(128) NOT NULL COMMENT '物理表名',
    `column_name`   VARCHAR(128) NOT NULL COMMENT '列名（记录存在即对角色不可见）',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_ds_table_col` (`role_id`, `datasource_id`, `table_name`, `column_name`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-隐藏列（黑名单）';

-- 用户表（带身份源抽象：兼容本系统账号 / 钉钉 / 飞书 / 企业微信）
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名；外部身份源用户=身份源昵称（可重名，靠 idp_user_id 区分）',
    `password_hash` VARCHAR(255) NULL COMMENT 'BCrypt 哈希，仅 LOCAL 身份源使用；外部身份源用户为空',
    `display_name`  VARCHAR(64)  NOT NULL COMMENT '显示名',
    `role_id`       INT NOT NULL DEFAULT 0 COMMENT '角色ID；0=未分配角色（无任何表权限）',
    `is_super_admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否超级管理员:0否,1是',
    `idp_type`      VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '身份源：LOCAL=本系统账号 / DINGTALK / FEISHU / WECOM',
    `idp_user_id`   VARCHAR(64)  NULL COMMENT '身份源里的用户ID（LOCAL为空）',
    `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `creator_id`    BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater_id`    BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_idp` (`idp_type`, `idp_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
-- 说明：username 用普通索引而非唯一键——外部身份源昵称允许重名（身份靠 idp_user_id 区分）；
-- LOCAL 本地账号的用户名唯一性由应用层（AuthService 创建用户时）保证。
-- 逻辑删除后不唯一 key，同身份源用户可重新创建。

CREATE TABLE IF NOT EXISTS `user_session` (
    `user_id`    INT          NOT NULL COMMENT '用户ID，关联 sys_user.id',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID（对应 agentscope_sessions.session_id）',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `session_id`),
    UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-会话归属表';

CREATE TABLE IF NOT EXISTS `mcp_server` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(255) NOT NULL COMMENT 'MCP Server 名称',
    `transport_type` VARCHAR(32) DEFAULT NULL COMMENT '传输类型',
    `client_type` VARCHAR(32) DEFAULT NULL COMMENT '客户端类型',
    `command` VARCHAR(500) DEFAULT NULL COMMENT '启动命令',
    `args` TEXT DEFAULT NULL COMMENT '启动参数',
    `env` TEXT DEFAULT NULL COMMENT '环境变量',
    `url` VARCHAR(1000) DEFAULT NULL COMMENT '服务 URL',
    `headers` TEXT DEFAULT NULL COMMENT '请求头',
    `query_params` TEXT DEFAULT NULL COMMENT '查询参数',
    `timeout` BIGINT DEFAULT NULL COMMENT '超时时间',
    `initialization_timeout` BIGINT DEFAULT NULL COMMENT '初始化超时时间',
    `enable_elicitation` TINYINT(1) DEFAULT NULL COMMENT '是否启用 elicitation',
    `http_version` VARCHAR(32) DEFAULT NULL COMMENT 'HTTP 版本',
    `connect_timeout` BIGINT DEFAULT NULL COMMENT '连接超时时间',
    `redirect_policy` VARCHAR(32) DEFAULT NULL COMMENT '重定向策略',
    `status` VARCHAR(32) DEFAULT NULL COMMENT '状态',
    `description` TEXT DEFAULT NULL COMMENT '描述',
    `creator_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mcp_server_name` (`name`),
    KEY `idx_mcp_server_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Server 配置表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id INT NOT NULL,
    permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色功能权限';

-- CSV import registry (MySQL 5.7). Physical CSV tables live in a separate database.
CREATE TABLE IF NOT EXISTS `csv_import` (
    `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `datasource_id` INT NOT NULL,
    `display_name` VARCHAR(200) NOT NULL,
    `description` VARCHAR(400) DEFAULT NULL,
    `original_filename` VARCHAR(255) NOT NULL,
    `file_sha256` CHAR(64) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `physical_table_name` VARCHAR(64) NOT NULL,
    `attempt_id` CHAR(32) NOT NULL DEFAULT '' COMMENT '执行批次标识',
    `submitted_by` INT DEFAULT NULL COMMENT '本次提交执行的用户ID',
    `parse_options_json` JSON NOT NULL,
    `columns_json` JSON NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `processed_rows` BIGINT NOT NULL DEFAULT 0,
    `row_count` BIGINT NOT NULL DEFAULT 0,
    `error_message` VARCHAR(500) DEFAULT NULL,
    `expires_at` DATETIME DEFAULT NULL,
    `creator_id` BIGINT DEFAULT NULL,
    `updater_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT NULL,
    `update_time` DATETIME DEFAULT NULL,
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_csv_datasource_table` (`datasource_id`, `physical_table_name`),
    KEY `idx_csv_status_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CSV 导入任务与托管表';

-- 用户个人访问令牌（PAT）：只保存哈希，明文仅在创建时返回一次。
CREATE TABLE IF NOT EXISTS `user_pat` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`       INT NOT NULL COMMENT '用户ID，关联 sys_user.id',
    `name`          VARCHAR(64) NOT NULL COMMENT '令牌名称',
    `token_prefix`  VARCHAR(16) NOT NULL COMMENT '令牌前缀，用于列表展示',
    `token_hash`    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 十六进制哈希',
    `expires_at`    DATETIME NOT NULL COMMENT '过期时间',
    `last_used_at`  DATETIME NULL COMMENT '最后使用时间',
    `revoked_at`    DATETIME NULL COMMENT '吊销时间；NULL 表示未吊销',
    `creator_id`    BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater_id`    BIGINT DEFAULT NULL COMMENT '修改人ID',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pat_token_hash` (`token_hash`),
    KEY `idx_user_pat_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户个人访问令牌';
