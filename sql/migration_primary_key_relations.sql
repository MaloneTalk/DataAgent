-- One-time MySQL 5.7 migration for primary-key-based semantic table references.
-- Back up the database first. Validate all references before altering application tables,
-- so fixing unmatched legacy names and rerunning does not hit an already-added column.

DROP PROCEDURE IF EXISTS `check_primary_key_relation_migration`;
DELIMITER $$
CREATE PROCEDURE `check_primary_key_relation_migration`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `column_info` c
        LEFT JOIN `table_info` t
            ON t.`datasource_id` = c.`datasource_id`
           AND LOWER(t.`table_name`) = LOWER(c.`table_name`)
        WHERE t.`id` IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'column_info has table names missing from table_info';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM `logical_table_relation` r
        LEFT JOIN `table_info` source_table
            ON source_table.`datasource_id` = r.`datasource_id`
           AND LOWER(source_table.`table_name`) = LOWER(r.`source_table_name`)
        LEFT JOIN `table_info` target_table
            ON target_table.`datasource_id` = r.`datasource_id`
           AND LOWER(target_table.`table_name`) = LOWER(r.`target_table_name`)
        WHERE source_table.`id` IS NULL OR target_table.`id` IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'logical_table_relation has table names missing from table_info';
    END IF;
END$$
DELIMITER ;
CALL `check_primary_key_relation_migration`();
DROP PROCEDURE `check_primary_key_relation_migration`;

ALTER TABLE `column_info`
    ADD COLUMN `table_id` INT NULL COMMENT '关联表信息ID' AFTER `datasource_id`;

UPDATE `column_info` c
INNER JOIN `table_info` t
    ON t.`datasource_id` = c.`datasource_id`
   AND LOWER(t.`table_name`) = LOWER(c.`table_name`)
SET c.`table_id` = t.`id`;

ALTER TABLE `column_info`
    MODIFY COLUMN `table_id` INT NOT NULL COMMENT '关联表信息ID',
    DROP INDEX `uk_datasource_table_column`,
    DROP INDEX `idx_datasource_table_visible`,
    DROP INDEX `idx_datasource_table_visible_column`,
    ADD UNIQUE KEY `uk_table_column` (`table_id`, `column_name`),
    ADD KEY `idx_datasource_id` (`datasource_id`),
    ADD CONSTRAINT `fk_column_info_table`
        FOREIGN KEY (`table_id`) REFERENCES `table_info` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    DROP COLUMN `table_name`;

ALTER TABLE `logical_table_relation`
    ADD COLUMN `source_table_id` INT NULL COMMENT '源表信息ID' AFTER `datasource_id`,
    ADD COLUMN `target_table_id` INT NULL COMMENT '目标表信息ID'
        AFTER `source_column_names_json`;

UPDATE `logical_table_relation` relation_meta
INNER JOIN `table_info` source_table
    ON source_table.`datasource_id` = relation_meta.`datasource_id`
   AND LOWER(source_table.`table_name`) = LOWER(relation_meta.`source_table_name`)
INNER JOIN `table_info` target_table
    ON target_table.`datasource_id` = relation_meta.`datasource_id`
   AND LOWER(target_table.`table_name`) = LOWER(relation_meta.`target_table_name`)
SET relation_meta.`source_table_id` = source_table.`id`,
    relation_meta.`target_table_id` = target_table.`id`;

ALTER TABLE `logical_table_relation`
    MODIFY COLUMN `source_table_id` INT NOT NULL COMMENT '源表信息ID',
    MODIFY COLUMN `target_table_id` INT NOT NULL COMMENT '目标表信息ID',
    DROP INDEX `idx_relation_source_signature`,
    DROP INDEX `idx_relation_source_table`,
    DROP INDEX `idx_relation_source_enabled`,
    DROP INDEX `idx_relation_source_enabled_id`,
    DROP INDEX `idx_relation_source_target_id`,
    ADD KEY `idx_relation_source_table` (`source_table_id`),
    ADD KEY `idx_relation_target_table` (`target_table_id`),
    ADD KEY `idx_relation_source_enabled_id`
        (`datasource_id`, `source_table_id`, `is_enabled`, `id`),
    ADD CONSTRAINT `fk_relation_source_table`
        FOREIGN KEY (`source_table_id`) REFERENCES `table_info` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_relation_target_table`
        FOREIGN KEY (`target_table_id`) REFERENCES `table_info` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    DROP COLUMN `source_table_name`,
    DROP COLUMN `target_table_name`,
    DROP COLUMN `source_column_signature`,
    DROP COLUMN `target_column_signature`;
