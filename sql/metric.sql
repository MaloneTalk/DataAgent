CREATE TABLE IF NOT EXISTS `metric_info` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `datasource_id` INT NOT NULL COMMENT '关联数据源ID',
    `metric_key` VARCHAR(255) NOT NULL COMMENT '指标稳定标识(程序化引用用),如 sales',
    `name` VARCHAR(255) NOT NULL COMMENT '指标显示名,如 销售额',
    `aliases` TEXT DEFAULT NULL COMMENT '同义词,逗号分隔,如 GMV,营收,流水',
    `measure_expr` VARCHAR(500) DEFAULT NULL COMMENT '度量表达式,如 SUM(paid_amount)',
    `filters` TEXT DEFAULT NULL COMMENT '过滤条件,如 status=''paid'' AND is_test=0',
    `time_field` VARCHAR(255) DEFAULT NULL COMMENT '时间维度字段,如 settle_time',
    `description` TEXT DEFAULT NULL COMMENT '业务口径说明/规则',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_metric_key` (`datasource_id`, `metric_key`),
    KEY `idx_datasource_name` (`datasource_id`, `name`),
    KEY `idx_datasource_aliases` (`datasource_id`, `aliases`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标口径表';

-- 示例:销售额口径。请按你实际的 datasource_id 调整后再执行(取消注释)。
-- INSERT INTO `metric_info`
--   (`datasource_id`, `metric_key`, `name`, `aliases`,
--    `measure_expr`, `filters`, `time_field`, `description`)
-- VALUES
--   (1, 'sales', '销售额', 'GMV,营收,流水,成交额',
--    'SUM(paid_amount)', 'status=''paid'' AND is_test=0', 'settle_time',
--    '已支付净额,使用结算时间,排除测试账号');
