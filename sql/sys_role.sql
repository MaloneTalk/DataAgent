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
