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