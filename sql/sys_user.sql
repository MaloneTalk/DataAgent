-- 用户表（带身份源抽象：兼容本系统账号 / 钉钉 / 飞书 / 企业微信）
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名；外部身份源用户=身份源昵称（可重名，靠 uk_idp 区分）',
    `password_hash` VARCHAR(255) NULL COMMENT 'PBKDF2 哈希，仅 LOCAL 身份源使用；外部身份源用户为空',
    `display_name`  VARCHAR(64)  NOT NULL COMMENT '显示名',
    `role_id`       INT NOT NULL DEFAULT 0 COMMENT '角色ID；0=未分配角色（无任何表权限）',
    `idp_type`      VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '身份源：LOCAL=本系统账号 / DINGTALK / FEISHU / WECOM',
    `idp_user_id`   VARCHAR(64)  NULL COMMENT '身份源里的用户ID（LOCAL为空）',
    `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    UNIQUE KEY `uk_idp` (`idp_type`, `idp_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
-- 说明：username 用普通索引而非唯一键——外部身份源昵称允许重名（身份靠 uk_idp 区分）；
-- LOCAL 本地账号的用户名唯一性由应用层（AuthService 创建用户时）保证。
