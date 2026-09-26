SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'table_info'
          AND COLUMN_NAME = 'data_granularity'
    ),
    'SELECT 1',
    'ALTER TABLE `table_info` ADD COLUMN `data_granularity` VARCHAR(500) DEFAULT NULL COMMENT ''数据粒度（一行代表的业务实体或事件）'' AFTER `table_description`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'column_info'
          AND COLUMN_NAME = 'semantic_type'
    ),
    'SELECT 1',
    'ALTER TABLE `column_info` ADD COLUMN `semantic_type` VARCHAR(32) DEFAULT NULL COMMENT ''语义类型：IDENTIFIER/DIMENSION/MEASURE/TIME/LOCATION'' AFTER `column_description`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'is_super_admin'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `is_super_admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否超级管理员:0否,1是'' AFTER `role_id`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- sys_user 审计字段：creator_id / create_time / updater_id / update_time / is_deleted
SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'creator_id'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `creator_id` BIGINT DEFAULT NULL COMMENT ''创建人ID'' AFTER `status`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'create_time'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER `creator_id`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'updater_id'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `updater_id` BIGINT DEFAULT NULL COMMENT ''修改人ID'' AFTER `create_time`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'update_time'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `updater_id`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'is_deleted'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user` ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除标记:0-未删除,1-已删除'' AFTER `update_time`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- 逻辑删除后允许同身份源用户重建：唯一键改为普通索引
SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND INDEX_NAME = 'uk_idp'
    ),
    'ALTER TABLE `sys_user` DROP INDEX `uk_idp`, ADD KEY `idx_idp` (`idp_type`, `idp_user_id`)',
    IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sys_user'
              AND INDEX_NAME = 'idx_idp'
        ),
        'SELECT 1',
        'ALTER TABLE `sys_user` ADD KEY `idx_idp` (`idp_type`, `idp_user_id`)'
    )
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
