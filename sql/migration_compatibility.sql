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
