-- MySQL 5.7: upgrade legacy metadata and migrate semantic references to table_info.id.
-- Back up the database and stop writes before running this single migration script.
-- ALTER TABLE commits implicitly. Every schema step is conditional so an interrupted
-- migration can be rerun after the cause of failure has been fixed.

DROP PROCEDURE IF EXISTS `migrate_primary_key_relations`;
DELIMITER $$
CREATE PROCEDURE `migrate_primary_key_relations`()
BEGIN
    DECLARE old_column_name INT DEFAULT 0;
    DECLARE old_source_name INT DEFAULT 0;
    DECLARE old_target_name INT DEFAULT 0;
    DECLARE has_column_id INT DEFAULT 0;
    DECLARE has_source_id INT DEFAULT 0;
    DECLARE has_target_id INT DEFAULT 0;

    SELECT COUNT(*) INTO old_column_name FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info' AND COLUMN_NAME = 'table_name';
    SELECT COUNT(*) INTO old_source_name FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
      AND COLUMN_NAME = 'source_table_name';
    SELECT COUNT(*) INTO old_target_name FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
      AND COLUMN_NAME = 'target_table_name';
    SELECT COUNT(*) INTO has_column_id FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info' AND COLUMN_NAME = 'table_id';
    SELECT COUNT(*) INTO has_source_id FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
      AND COLUMN_NAME = 'source_table_id';
    SELECT COUNT(*) INTO has_target_id FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
      AND COLUMN_NAME = 'target_table_id';

    IF (old_column_name = 0 AND has_column_id = 0)
       OR (old_source_name = 0 AND has_source_id = 0)
       OR (old_target_name = 0 AND has_target_id = 0) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unsupported schema: table name and ID are both absent';
    END IF;

    -- Validate legacy data before the first ALTER TABLE. Never guess which duplicate
    -- table should own a column or relation.
    IF EXISTS (
        SELECT 1 FROM `table_info`
        GROUP BY `datasource_id`, LOWER(`table_name`)
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'table_info has duplicate datasource/table names';
    END IF;
    IF old_column_name = 1 THEN
        IF EXISTS (
            SELECT 1 FROM `column_info`
            GROUP BY `datasource_id`, LOWER(`table_name`), LOWER(`column_name`)
            HAVING COUNT(*) > 1
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'column_info has duplicate datasource/table/column names';
        END IF;
        IF EXISTS (
            SELECT 1 FROM `column_info` c
            LEFT JOIN `table_info` t ON t.`datasource_id` = c.`datasource_id`
                AND LOWER(t.`table_name`) = LOWER(c.`table_name`)
            WHERE t.`id` IS NULL
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'column_info has table names missing from table_info';
        END IF;
    END IF;
    IF old_source_name = 1 THEN
        IF EXISTS (
            SELECT 1 FROM `logical_table_relation` r
            LEFT JOIN `table_info` t ON t.`datasource_id` = r.`datasource_id`
                AND LOWER(t.`table_name`) = LOWER(r.`source_table_name`)
            WHERE t.`id` IS NULL
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'logical_table_relation has missing source tables';
        END IF;
    END IF;
    IF old_target_name = 1 THEN
        IF EXISTS (
            SELECT 1 FROM `logical_table_relation` r
            LEFT JOIN `table_info` t ON t.`datasource_id` = r.`datasource_id`
                AND LOWER(t.`table_name`) = LOWER(r.`target_table_name`)
            WHERE t.`id` IS NULL
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'logical_table_relation has missing target tables';
        END IF;
    END IF;

    IF has_column_id = 0 THEN
        ALTER TABLE `column_info`
            ADD COLUMN `table_id` INT NULL COMMENT '关联表信息ID' AFTER `datasource_id`;
    END IF;
    IF has_source_id = 0 THEN
        ALTER TABLE `logical_table_relation`
            ADD COLUMN `source_table_id` INT NULL COMMENT '源表信息ID' AFTER `datasource_id`;
    END IF;
    IF has_target_id = 0 THEN
        ALTER TABLE `logical_table_relation`
            ADD COLUMN `target_table_id` INT NULL COMMENT '目标表信息ID'
            AFTER `source_column_names_json`;
    END IF;

    IF old_column_name = 1 THEN
        UPDATE `column_info` c
        INNER JOIN `table_info` t ON t.`datasource_id` = c.`datasource_id`
            AND LOWER(t.`table_name`) = LOWER(c.`table_name`)
        SET c.`table_id` = t.`id`;
    END IF;
    IF old_source_name = 1 THEN
        UPDATE `logical_table_relation` r
        INNER JOIN `table_info` t ON t.`datasource_id` = r.`datasource_id`
            AND LOWER(t.`table_name`) = LOWER(r.`source_table_name`)
        SET r.`source_table_id` = t.`id`;
    END IF;
    IF old_target_name = 1 THEN
        UPDATE `logical_table_relation` r
        INNER JOIN `table_info` t ON t.`datasource_id` = r.`datasource_id`
            AND LOWER(t.`table_name`) = LOWER(r.`target_table_name`)
        SET r.`target_table_id` = t.`id`;
    END IF;

    -- Validate IDs even when resuming from a partially migrated schema.
    IF EXISTS (
        SELECT 1 FROM `column_info` c
        LEFT JOIN `table_info` t ON t.`id` = c.`table_id`
            AND t.`datasource_id` = c.`datasource_id`
        WHERE t.`id` IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'column_info has invalid table_id references';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `column_info`
        GROUP BY `table_id`, LOWER(`column_name`)
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'column_info has duplicate table_id/column names';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `logical_table_relation` r
        LEFT JOIN `table_info` s ON s.`id` = r.`source_table_id`
            AND s.`datasource_id` = r.`datasource_id`
        LEFT JOIN `table_info` t ON t.`id` = r.`target_table_id`
            AND t.`datasource_id` = r.`datasource_id`
        WHERE s.`id` IS NULL OR t.`id` IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'logical_table_relation has invalid table_id references';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND COLUMN_NAME = 'table_id' AND IS_NULLABLE = 'YES'
    ) THEN
        ALTER TABLE `column_info`
            MODIFY COLUMN `table_id` INT NOT NULL COMMENT '关联表信息ID';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND INDEX_NAME = 'uk_table_column'
    ) THEN
        ALTER TABLE `column_info`
            ADD UNIQUE KEY `uk_table_column` (`table_id`, `column_name`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND INDEX_NAME = 'idx_datasource_id'
    ) THEN
        ALTER TABLE `column_info` ADD KEY `idx_datasource_id` (`datasource_id`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND CONSTRAINT_NAME = 'fk_column_info_table'
    ) THEN
        ALTER TABLE `column_info`
            ADD CONSTRAINT `fk_column_info_table`
            FOREIGN KEY (`table_id`) REFERENCES `table_info` (`id`)
            ON DELETE CASCADE ON UPDATE RESTRICT;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND INDEX_NAME = 'uk_datasource_table_column'
    ) THEN
        ALTER TABLE `column_info` DROP INDEX `uk_datasource_table_column`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND INDEX_NAME = 'idx_datasource_table_visible'
    ) THEN
        ALTER TABLE `column_info` DROP INDEX `idx_datasource_table_visible`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'column_info'
          AND INDEX_NAME = 'idx_datasource_table_visible_column'
    ) THEN
        ALTER TABLE `column_info` DROP INDEX `idx_datasource_table_visible_column`;
    END IF;
    IF old_column_name = 1 THEN
        ALTER TABLE `column_info` DROP COLUMN `table_name`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND COLUMN_NAME = 'source_table_id' AND IS_NULLABLE = 'YES'
    ) THEN
        ALTER TABLE `logical_table_relation`
            MODIFY COLUMN `source_table_id` INT NOT NULL COMMENT '源表信息ID';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND COLUMN_NAME = 'target_table_id' AND IS_NULLABLE = 'YES'
    ) THEN
        ALTER TABLE `logical_table_relation`
            MODIFY COLUMN `target_table_id` INT NOT NULL COMMENT '目标表信息ID';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_signature'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP INDEX `idx_relation_source_signature`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_enabled'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP INDEX `idx_relation_source_enabled`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_target_id'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP INDEX `idx_relation_source_target_id`;
    END IF;
    -- These two index names are reused; remove only the legacy definitions.
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_table' AND COLUMN_NAME = 'source_table_name'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP INDEX `idx_relation_source_table`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_enabled_id'
          AND COLUMN_NAME = 'source_table_name'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP INDEX `idx_relation_source_enabled_id`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_table'
    ) THEN
        ALTER TABLE `logical_table_relation`
            ADD KEY `idx_relation_source_table` (`source_table_id`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_target_table'
    ) THEN
        ALTER TABLE `logical_table_relation`
            ADD KEY `idx_relation_target_table` (`target_table_id`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND INDEX_NAME = 'idx_relation_source_enabled_id'
    ) THEN
        ALTER TABLE `logical_table_relation`
            ADD KEY `idx_relation_source_enabled_id`
                (`datasource_id`, `source_table_id`, `is_enabled`, `id`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND CONSTRAINT_NAME = 'fk_relation_source_table'
    ) THEN
        ALTER TABLE `logical_table_relation`
            ADD CONSTRAINT `fk_relation_source_table`
            FOREIGN KEY (`source_table_id`) REFERENCES `table_info` (`id`)
            ON DELETE CASCADE ON UPDATE RESTRICT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND CONSTRAINT_NAME = 'fk_relation_target_table'
    ) THEN
        ALTER TABLE `logical_table_relation`
            ADD CONSTRAINT `fk_relation_target_table`
            FOREIGN KEY (`target_table_id`) REFERENCES `table_info` (`id`)
            ON DELETE CASCADE ON UPDATE RESTRICT;
    END IF;
    IF old_source_name = 1 THEN
        ALTER TABLE `logical_table_relation` DROP COLUMN `source_table_name`;
    END IF;
    IF old_target_name = 1 THEN
        ALTER TABLE `logical_table_relation` DROP COLUMN `target_table_name`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND COLUMN_NAME = 'source_column_signature'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP COLUMN `source_column_signature`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logical_table_relation'
          AND COLUMN_NAME = 'target_column_signature'
    ) THEN
        ALTER TABLE `logical_table_relation` DROP COLUMN `target_column_signature`;
    END IF;
END$$
DELIMITER ;

CALL `migrate_primary_key_relations`();
DROP PROCEDURE `migrate_primary_key_relations`;

-- Compatibility changes for older metadata schemas follow. They are independently
-- conditional, so they can run after the primary-key migration and on reruns.
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

-- metric_info 审计字段：creator_id / updater_id
SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'metric_info'
          AND COLUMN_NAME = 'creator_id'
    ),
    'SELECT 1',
    'ALTER TABLE `metric_info` ADD COLUMN `creator_id` BIGINT DEFAULT NULL COMMENT ''创建人ID'' AFTER `description`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'metric_info'
          AND COLUMN_NAME = 'updater_id'
    ),
    'SELECT 1',
    'ALTER TABLE `metric_info` ADD COLUMN `updater_id` BIGINT DEFAULT NULL COMMENT ''修改人ID'' AFTER `create_time`'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- 逻辑删除后保留行以便审计：同 key 软删后允许重新创建，唯一键改为普通索引
SET @migration_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'metric_info'
          AND INDEX_NAME = 'uk_datasource_metric_key'
    ),
    'ALTER TABLE `metric_info` DROP INDEX `uk_datasource_metric_key`, ADD KEY `idx_datasource_metric_key` (`datasource_id`, `metric_key`)',
    IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'metric_info'
              AND INDEX_NAME = 'idx_datasource_metric_key'
        ),
        'SELECT 1',
        'ALTER TABLE `metric_info` ADD KEY `idx_datasource_metric_key` (`datasource_id`, `metric_key`)'
    )
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
